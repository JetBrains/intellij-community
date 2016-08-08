/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.PlatformUtils;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.pico.AssignableToComponentAdapter;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.InstanceComponentAdapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ServiceManagerImpl implements BaseComponent {
  private static final Logger LOG = Logger.getInstance(ServiceManagerImpl.class);

  private static final ExtensionPointName<ServiceDescriptor> APP_SERVICES = new ExtensionPointName<>("com.intellij.applicationService");
  private static final ExtensionPointName<ServiceDescriptor> PROJECT_SERVICES = new ExtensionPointName<>("com.intellij.projectService");
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

  protected void installEP(@NotNull ExtensionPointName<ServiceDescriptor> pointName, @NotNull final ComponentManager componentManager) {
    LOG.assertTrue(myExtensionPointName == null, "Already called installEP with " + myExtensionPointName);
    myExtensionPointName = pointName;
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(pointName);
    final MutablePicoContainer picoContainer = (MutablePicoContainer)componentManager.getPicoContainer();

    myExtensionPointListener = new ExtensionPointListener<ServiceDescriptor>() {
      @Override
      public void extensionAdded(@NotNull final ServiceDescriptor descriptor, final PluginDescriptor pluginDescriptor) {
        if (descriptor.overrides) {
          // Allow to re-define service implementations in plugins.
          ComponentAdapter oldAdapter = picoContainer.unregisterComponent(descriptor.getInterface());
          if (oldAdapter == null) {
            throw new RuntimeException("Service: " + descriptor.getInterface() + " doesn't override anything");
          }
        }

        if (!Extensions.isComponentSuitableForOs(descriptor.os)) {
          return;
        }

        // empty serviceImplementation means we want to unregister service
        if (!StringUtil.isEmpty(descriptor.getImplementation())) {
          picoContainer.registerComponent(new MyComponentAdapter(descriptor, pluginDescriptor, (ComponentManagerEx)componentManager));
        }
      }

      @Override
      public void extensionRemoved(@NotNull final ServiceDescriptor extension, final PluginDescriptor pluginDescriptor) {
        picoContainer.unregisterComponent(extension.getInterface());
      }
    };
    extensionPoint.addExtensionPointListener(myExtensionPointListener);
  }

  public List<ServiceDescriptor> getAllDescriptors() {
    ServiceDescriptor[] extensions = Extensions.getExtensions(myExtensionPointName);
    return Arrays.asList(extensions);
  }

  public static void processAllImplementationClasses(@NotNull ComponentManagerImpl componentManager, @NotNull PairProcessor<Class<?>, PluginDescriptor> processor) {
    Collection adapters = componentManager.getPicoContainer().getComponentAdapters();
    if (adapters.isEmpty()) {
      return;
    }

    for (Object o : adapters) {
      Class aClass;
      if (o instanceof MyComponentAdapter) {
        MyComponentAdapter adapter = (MyComponentAdapter)o;
        PluginDescriptor pluginDescriptor = adapter.myPluginDescriptor;
        try {
          ComponentAdapter delegate = adapter.myDelegate;
          // avoid delegation creation & class initialization
          if (delegate == null) {
            ClassLoader classLoader = pluginDescriptor == null ? ServiceManagerImpl.class.getClassLoader() : pluginDescriptor.getPluginClassLoader();
            aClass = Class.forName(adapter.myDescriptor.getImplementation(), false, classLoader);
          }
          else {
            aClass = delegate.getComponentImplementation();
          }
        }
        catch (Throwable e) {
          if (PlatformUtils.isIdeaUltimate()) {
            LOG.error(e);
          }
          else {
            // well, component registered, but required jar is not added to classpath (community edition or junior IDE)
            LOG.warn(e);
          }
          continue;
        }

        if (!processor.process(aClass, pluginDescriptor)) {
          break;
        }
      }
      else if (o instanceof ComponentAdapter && !(o instanceof ExtensionComponentAdapter)) {
        PluginId pluginId = componentManager.getConfig((ComponentAdapter)o);
        // allow InstanceComponentAdapter without pluginId to test
        if (pluginId != null || o instanceof InstanceComponentAdapter) {
          try {
            aClass = ((ComponentAdapter)o).getComponentImplementation();
          }
          catch (Throwable e) {
            LOG.error(e);
            continue;
          }

          processor.process(aClass, pluginId == null ? null : PluginManager.getPlugin(pluginId));
        }
      }
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(myExtensionPointName);
    extensionPoint.removeExtensionPointListener(myExtensionPointListener);
  }

  private static class MyComponentAdapter implements AssignableToComponentAdapter {
    private ComponentAdapter myDelegate;
    private final ServiceDescriptor myDescriptor;
    private final PluginDescriptor myPluginDescriptor;
    private final ComponentManagerEx myComponentManager;
    private volatile Object myInitializedComponentInstance;

    public MyComponentAdapter(final ServiceDescriptor descriptor, final PluginDescriptor pluginDescriptor, ComponentManagerEx componentManager) {
      myDescriptor = descriptor;
      myPluginDescriptor = pluginDescriptor;
      myComponentManager = componentManager;
      myDelegate = null;
    }

    @Override
    public String getComponentKey() {
      return myDescriptor.getInterface();
    }

    @Override
    public Class getComponentImplementation() {
      return getDelegate().getComponentImplementation();
    }

    @Override
    public Object getComponentInstance(@NotNull PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      Object instance = myInitializedComponentInstance;
      if (instance != null) {
        return instance;
      }

      synchronized (this) {
        instance = myInitializedComponentInstance;
        if (instance != null) {
          // DCL is fine, field is volatile
          return instance;
        }

        ComponentAdapter delegate = getDelegate();

        if (LOG.isDebugEnabled() &&
            ApplicationManager.getApplication().isWriteAccessAllowed() &&
            !ApplicationManager.getApplication().isUnitTestMode() &&
            PersistentStateComponent.class.isAssignableFrom(delegate.getComponentImplementation())) {
          LOG.warn(new Throwable("Getting service from write-action leads to possible deadlock. Service implementation " + myDescriptor.getImplementation()));
        }

        // prevent storages from flushing and blocking FS
        AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Creating component '" + myDescriptor.getImplementation() + "'");
        try {
          instance = delegate.getComponentInstance(container);
          if (instance instanceof Disposable) {
            Disposer.register(myComponentManager, (Disposable)instance);
          }

          myComponentManager.initializeComponent(instance, true);

          myInitializedComponentInstance = instance;
          return instance;
        }
        finally {
          token.finish();
        }
      }
    }

    @NotNull
    private synchronized ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        Class<?> implClass;
        try {
          ClassLoader classLoader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
          implClass = Class.forName(myDescriptor.getImplementation(), true, classLoader);
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }

        myDelegate = new CachingConstructorInjectionComponentAdapter(getComponentKey(), implClass, null, true);
      }
      return myDelegate;
    }

    @Override
    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    @Override
    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
    }

    @Override
    public String getAssignableToClassName() {
      return myDescriptor.getInterface();
    }

    @Override
    public String toString() {
      return "ServiceComponentAdapter[" + myDescriptor.getInterface() + "]: implementation=" + myDescriptor.getImplementation() + ", plugin=" + myPluginDescriptor;
    }
  }
}
