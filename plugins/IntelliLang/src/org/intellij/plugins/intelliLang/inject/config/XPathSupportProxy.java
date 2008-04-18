/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;

/**
 * Proxy class that allows to avoid a hard compile time dependency on the XPathView plugin.
 */
public abstract class XPathSupportProxy {
    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.intelliLang.inject.config.XPathSupportProxy");

    public static final Object UNSUPPORTED = "UNSUPPORTED";
    public static final Object INVALID = "INVALID";

    // helper interfaces... must be public
    public interface _XPathSupport {
        XPath createXPath(XmlFile file, String expression, Collection namespaces) throws JaxenException;
    }
    public interface _ContextProvider {
        void attachTo(PsiFile file);
    }

    @NotNull
    public abstract XPath createXPath(String expression) throws JaxenException;

    public abstract void attachContext(@NotNull PsiFile file);

    private static XPathSupportProxy ourInstance;
    private static boolean isInitialized;

    @Nullable
    public static synchronized XPathSupportProxy getInstance() {
        if (isInitialized) {
            return ourInstance;
        }
        isInitialized = true;

        final PluginId id = PluginId.getId("XPathView");
        final IdeaPluginDescriptor plugin = ApplicationManager.getApplication().getPlugin(id);

        final BaseComponent c = ApplicationManager.getApplication().getComponent("XPathView.XPathSupport");
        if (plugin == null || c == null) {
            LOG.info("XPathView components not found");
            return null;
        }

        try {
            return ourInstance = new MyXPathSupportProxy(plugin, c);
        } catch (Throwable e) {
            LOG.error("Error creating XPath context provider", e);
            return null;
        }
    }

    private static class MyXPathSupportProxy extends XPathSupportProxy {
        private final _ContextProvider myProvider;
        private final _XPathSupport mySupport;

        public MyXPathSupportProxy(IdeaPluginDescriptor plugin, final BaseComponent component) throws Throwable {
            final ClassLoader pluginLoader = plugin.getPluginClassLoader();
            final Class provClass = pluginLoader.loadClass("org.intellij.lang.xpath.context.ContextProvider");
            final Class ctxTypeClass = pluginLoader.loadClass("org.intellij.lang.xpath.context.ContextType");
            final Class xpathTypeClass = pluginLoader.loadClass("org.intellij.lang.xpath.psi.XPathType");

            final Field ctxField = ctxTypeClass.getField("INTERACTIVE");
            final Object ctxType = ctxField.get(null);

            final Field booleanField = xpathTypeClass.getField("BOOLEAN");
            final Object booleanType = booleanField.get(null);

            final Enhancer e = new Enhancer();
            e.setSuperclass(provClass);
            e.setInterfaces(new Class[]{ _ContextProvider.class });

            final CompositeClassLoader loader = new CompositeClassLoader();
            loader.add(getClass().getClassLoader());
            loader.add(pluginLoader);
            e.setClassLoader(loader);

            e.setCallback(new MethodInterceptor() {
                @SuppressWarnings({ "ConstantConditions", "StringEquality" })
                public Object intercept(Object object, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
                    final String name = method.getName();
                    if (name == "getContextType") {
                        return ctxType;
                    } else if (name == "getExpectedType") {
                        return booleanType;
                    } else if (Modifier.isAbstract(method.getModifiers())) {
                        return null;
                    } else {
                        return methodProxy.invokeSuper(object, args);
                    }
                }
            });
            myProvider = (_ContextProvider)e.create();

            final Enhancer e2 = new Enhancer();
            e2.setClassLoader(loader);
            e2.setSuperclass(component.getClass().getSuperclass()); // XPathSupportImpl -> XPathSupport
            e2.setInterfaces(new Class[]{ _XPathSupport.class });

            e2.setCallback(new MethodInterceptor() {
                public Object intercept(Object object, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                    try {
                        return method.invoke(component, objects);
                    } catch (InvocationTargetException e1) {
                        throw e1.getTargetException();
                    }
                }
            });
            mySupport = (_XPathSupport)e2.create();

            // validate the API
            mySupport.createXPath(null, "a", Collections.emptyList());
        }

        public void attachContext(@NotNull PsiFile file) {
            try {
                myProvider.attachTo(file);
            } catch (Throwable e) {
                LOG.error(e);
                synchronized (getClass()) {
                    ourInstance = null;
                }
            }
        }

        @NotNull
        public XPath createXPath(final String expression) throws JaxenException {
            // the XmlFile is usually used to determine namespace context which can be quite expensive though.
            return mySupport.createXPath(null, expression, Collections.emptyList());
        }
    }
}