/*
 * The MIT License
 *
 * Copyright 2014 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists;

import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Aggregates several whitelists.
 */
public class ProxyWhitelist extends Whitelist {
    
    private Collection<? extends Whitelist> originalDelegates;
    private final List<Whitelist> delegates = new ArrayList<Whitelist>();
    private final List<EnumeratingWhitelist.MethodSignature> methodSignatures = new ArrayList<EnumeratingWhitelist.MethodSignature>();
    private final List<EnumeratingWhitelist.NewSignature> newSignatures = new ArrayList<EnumeratingWhitelist.NewSignature>();
    private final List<EnumeratingWhitelist.StaticMethodSignature> staticMethodSignatures = new ArrayList<EnumeratingWhitelist.StaticMethodSignature>();
    private final List<EnumeratingWhitelist.FieldSignature> fieldSignatures = new ArrayList<EnumeratingWhitelist.FieldSignature>();
    /** anything wrapping us, so that we can propagate {@link #reset} calls up the chain */
    private final Map<ProxyWhitelist,Void> wrappers = new WeakHashMap<ProxyWhitelist,Void>();

    public ProxyWhitelist(Collection<? extends Whitelist> delegates) {
        reset(delegates);
    }

    private void reset() {
        reset(originalDelegates);
    }

    public final void reset(Collection<? extends Whitelist> delegates) {
        originalDelegates = delegates;
        this.delegates.clear();
        methodSignatures.clear();
        newSignatures.clear();
        staticMethodSignatures.clear();
        fieldSignatures.clear();
        this.delegates.add(new EnumeratingWhitelist() {
            @Override protected List<EnumeratingWhitelist.MethodSignature> methodSignatures() {
                return methodSignatures;
            }
            @Override protected List<EnumeratingWhitelist.NewSignature> newSignatures() {
                return newSignatures;
            }
            @Override protected List<EnumeratingWhitelist.StaticMethodSignature> staticMethodSignatures() {
                return staticMethodSignatures;
            }
            @Override protected List<EnumeratingWhitelist.FieldSignature> fieldSignatures() {
                return fieldSignatures;
            }
        });
        for (Whitelist delegate : delegates) {
            if (delegate instanceof EnumeratingWhitelist) {
                EnumeratingWhitelist ew = (EnumeratingWhitelist) delegate;
                methodSignatures.addAll(ew.methodSignatures());
                newSignatures.addAll(ew.newSignatures());
                staticMethodSignatures.addAll(ew.staticMethodSignatures());
                fieldSignatures.addAll(ew.fieldSignatures());
            } else if (delegate instanceof ProxyWhitelist) {
                ProxyWhitelist pw = (ProxyWhitelist) delegate;
                pw.wrappers.put(this, null);
                for (Whitelist subdelegate : pw.delegates) {
                    if (subdelegate instanceof EnumeratingWhitelist) {
                        continue; // this is handled specially
                    }
                    this.delegates.add(subdelegate);
                }
                methodSignatures.addAll(pw.methodSignatures);
                newSignatures.addAll(pw.newSignatures);
                staticMethodSignatures.addAll(pw.staticMethodSignatures);
                fieldSignatures.addAll(pw.fieldSignatures);
            } else {
                this.delegates.add(delegate);
            }
        }
        for (ProxyWhitelist pw : wrappers.keySet()) {
            pw.reset();
        }
    }

    public ProxyWhitelist(Whitelist... delegates) {
        this(Arrays.asList(delegates));
    }

    @Override public final boolean permitsMethod(Object receiver, String method, Object[] args) {
        for (Whitelist delegate : delegates) {
            if (delegate.permitsMethod(receiver, method, args)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsNew(Class<?> receiver, Object[] args) {
        for (Whitelist delegate : delegates) {
            if (delegate.permitsNew(receiver, args)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsStaticMethod(Class<?> receiver, String method, Object[] args) {
        for (Whitelist delegate : delegates) {
            if (delegate.permitsStaticMethod(receiver, method, args)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsFieldGet(Object receiver, String field) {
        for (Whitelist delegate : delegates) {
            if (delegate.permitsFieldGet(receiver, field)) {
                return true;
            }
        }
        return false;
    }

    @Override public final boolean permitsFieldSet(Object receiver, String field, Object value) {
        for (Whitelist delegate : delegates) {
            if (delegate.permitsFieldSet(receiver, field, value)) {
                return true;
            }
        }
        return false;
    }

}
