/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.pico.AssignableToComponentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

public class ServiceManagerImpl implements BaseComponent {
  private static final ExtensionPointName<ServiceDescriptor> APP_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.applicationService");
  private static final ExtensionPointName<ServiceDescriptor> PROJECT_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.projectService");
  private ExtensionPointName<ServiceDescriptor> myExtensionPointName;
  private ExtensionPointListener<ServiceDescriptor> myExtensionPointListener;

  public ServiceManagerImpl() {
    installEP(APP_SERVICES, ApplicationManager.getApplication());
  }

  public ServiceManagerImpl(Project project) {
    installEP(PROJECT_SERVICES, project);
  }

  protected ServiceManagerImpl(boolean ignoreInit) {
  }

  protected void installEP(final ExtensionPointName<ServiceDescriptor> pointName, final ComponentManager componentManager) {
    myExtensionPointName = pointName;
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(pointName);
    assert extensionPoint != null;

    final MutablePicoContainer picoContainer = (MutablePicoContainer)componentManager.getPicoContainer();

    myExtensionPointListener = new ExtensionPointListener<ServiceDescriptor>() {
      public void extensionAdded(final ServiceDescriptor descriptor, final PluginDescriptor pluginDescriptor) {
        if (descriptor.overrides) {
          ComponentAdapter oldAdapter =
            picoContainer.unregisterComponent(descriptor.serviceInterface);// Allow to re-define service implementations in plugins.
          if (oldAdapter == null) {
            throw new RuntimeException("Service: " + descriptor.serviceInterface + " doesn't override anything");
          }
        }

        picoContainer.registerComponent(new MyComponentAdapter(descriptor, pluginDescriptor, (ComponentManagerEx)componentManager));
      }

      public void extensionRemoved(final ServiceDescriptor extension, final PluginDescriptor pluginDescriptor) {
        picoContainer.unregisterComponent(extension.serviceInterface);
      }
    };
    extensionPoint.addExtensionPointListener(myExtensionPointListener);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(myExtensionPointName);
    assert extensionPoint != null;
    extensionPoint.removeExtensionPointListener(myExtensionPointListener);
  }

  private static class MyComponentAdapter implements AssignableToComponentAdapter {
    private ComponentAdapter myDelegate;
    private final ServiceDescriptor myDescriptor;
    private final PluginDescriptor myPluginDescriptor;
    private final ComponentManagerEx myComponentManager;
    private volatile Object myInitializedComponentInstance = null;

    public MyComponentAdapter(final ServiceDescriptor descriptor, final PluginDescriptor pluginDescriptor, ComponentManagerEx componentManager) {
      myDescriptor = descriptor;
      myPluginDescriptor = pluginDescriptor;
      myComponentManager = componentManager;
      myDelegate = null;
    }

    public Object getComponentKey() {
      return myDescriptor.serviceInterface;
    }

    public Class getComponentImplementation() {
      return loadClass(myDescriptor.serviceInterface);
    }

    private Class loadClass(final String className) {
      try {
        final ClassLoader classLoader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();

        return Class.forName(className, true, classLoader);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      Object instance = myInitializedComponentInstance;
      if (instance != null) return instance;

      return ApplicationManager.getApplication().runReadAction(new Computable<Object>() {
        public Object compute() {
          synchronized (MyComponentAdapter.this) {
            Object instance = myInitializedComponentInstance;
            if (instance != null) return instance; // DCL is fine, field is volatile
            myInitializedComponentInstance = instance = initializeInstance(container);
            return instance;
          }
        }
      });
    }

    protected Object initializeInstance(final PicoContainer container) {
      final Object serviceInstance = getDelegate().getComponentInstance(container);
      if (serviceInstance instanceof Disposable) {
        Disposer.register(myComponentManager, (Disposable)serviceInstance);
      }

      myComponentManager.getComponentStore().initComponent(serviceInstance, true);

      return serviceInstance;
    }

    private synchronized ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(getComponentKey(), loadClass(myDescriptor.serviceImplementation), null, true));
      }

      return myDelegate;
    }

    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
    }

    public boolean isAssignableTo(Class aClass) {
      return aClass.getName().equals(getComponentKey());
    }

    public String getAssignableToClassName() {
      return myDescriptor.serviceInterface;
    }
  }
}
