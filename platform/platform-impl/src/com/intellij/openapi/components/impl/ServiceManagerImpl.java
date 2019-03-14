// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Activities;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.pico.AssignableToComponentAdapter;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;
import org.picocontainer.defaults.InstanceComponentAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static com.intellij.util.pico.DefaultPicoContainer.getActivityLevel;

public final class ServiceManagerImpl implements Disposable {
  private static final Logger LOG = Logger.getInstance(ServiceManagerImpl.class);

  static void registerServices(@NotNull List<ServiceDescriptor> services,
                               @NotNull IdeaPluginDescriptor pluginDescriptor,
                               @NotNull ComponentManagerEx componentManager) {
    MutablePicoContainer picoContainer = (MutablePicoContainer)componentManager.getPicoContainer();
    for (ServiceDescriptor descriptor : services) {
      // Allow to re-define service implementations in plugins.
      // empty serviceImplementation means we want to unregister service
      if (descriptor.overrides) {
        // Allow to re-define service implementations in plugins.
        ComponentAdapter oldAdapter = picoContainer.unregisterComponent(descriptor.getInterface());
        if (oldAdapter == null) {
          throw new PluginException("Service: " + descriptor.getInterface() + " doesn't override anything", pluginDescriptor.getPluginId());
        }
      }

      // empty serviceImplementation means we want to unregister service
      if (!StringUtil.isEmpty(descriptor.getImplementation())) {
        picoContainer.registerComponent(new MyComponentAdapter(descriptor, pluginDescriptor, componentManager));
      }
    }
  }

  @TestOnly
  public static void processAllDescriptors(@NotNull Consumer<ServiceDescriptor> consumer, @NotNull ComponentManager componentManager) {
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getLoadedPlugins(null)) {
      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)plugin;
      List<ServiceDescriptor> serviceDescriptors;
      if (componentManager instanceof Application) {
        serviceDescriptors = pluginDescriptor.getAppServices();
      }
      else if (componentManager instanceof Project) {
        serviceDescriptors = pluginDescriptor.getProjectServices();
      }
      else {
        serviceDescriptors = pluginDescriptor.getModuleServices();
      }

      for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
        consumer.accept(serviceDescriptor);
      }
    }
  }

  @NotNull
  public static List<String> getImplementationClassNames(@NotNull ComponentManager componentManager, @NotNull String prefix) {
    List<String> result = new ArrayList<>();
    //noinspection TestOnlyProblems
    processAllDescriptors(serviceDescriptor -> {
      String implementation = serviceDescriptor.getImplementation();
      if (!StringUtil.isEmpty(implementation) && implementation.startsWith(prefix)) {
        result.add(implementation);
      }
    }, componentManager);
    return result;
  }

  public static void processAllImplementationClasses(@NotNull ComponentManagerImpl componentManager,
                                                     @NotNull BiPredicate<? super Class<?>, ? super PluginDescriptor> processor) {
    @SuppressWarnings("unchecked")
    Collection<ComponentAdapter> adapters = componentManager.getPicoContainer().getComponentAdapters();
    if (adapters.isEmpty()) {
      return;
    }

    for (ComponentAdapter o : adapters) {
      Class<?> aClass;
      if (o instanceof MyComponentAdapter) {
        MyComponentAdapter adapter = (MyComponentAdapter)o;
        PluginDescriptor pluginDescriptor = adapter.myPluginDescriptor;
        try {
          ComponentAdapter delegate = adapter.myDelegate;
          // avoid delegation creation & class initialization
          if (delegate == null) {
            ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
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

        if (!processor.test(aClass, pluginDescriptor)) {
          break;
        }
      }
      else if (!(o instanceof ExtensionComponentAdapter)) {
        PluginId pluginId = componentManager.getConfig(o);
        // allow InstanceComponentAdapter without pluginId to test
        if (pluginId != null || o instanceof InstanceComponentAdapter) {
          try {
            aClass = o.getComponentImplementation();
          }
          catch (Throwable e) {
            LOG.error(e);
            continue;
          }

          if (!processor.test(aClass, pluginId == null ? null : PluginManager.getPlugin(pluginId))) {
            break;
          }
        }
      }
    }
  }

  @Override
  public void dispose() {
  }

  private static class MyComponentAdapter implements AssignableToComponentAdapter, DefaultPicoContainer.LazyComponentAdapter {
    private ComponentAdapter myDelegate = null;
    private final PluginDescriptor myPluginDescriptor;
    private final ServiceDescriptor myDescriptor;
    private final ComponentManagerEx myComponentManager;
    private volatile Object myInitializedComponentInstance;

    MyComponentAdapter(@NotNull ServiceDescriptor descriptor, @NotNull PluginDescriptor pluginDescriptor, @NotNull ComponentManagerEx componentManager) {
      myDescriptor = descriptor;
      myPluginDescriptor = pluginDescriptor;
      myComponentManager = componentManager;
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
    public boolean isComponentInstantiated() {
      return myInitializedComponentInstance != null;
    }

    @Override
    public Object getComponentInstance(@NotNull PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      Object instance = myInitializedComponentInstance;
      if (instance != null) {
        return instance;
      }

      //noinspection SynchronizeOnThis
      synchronized (this) {
        instance = myInitializedComponentInstance;
        if (instance != null) {
          // DCL is fine, field is volatile
          return instance;
        }

        String implementation = myDescriptor.getImplementation();
        if (LOG.isDebugEnabled() &&
            ApplicationManager.getApplication().isWriteAccessAllowed() &&
            !ApplicationManager.getApplication().isUnitTestMode() &&
            PersistentStateComponent.class.isAssignableFrom(getDelegate().getComponentImplementation())) {
          LOG.warn(new Throwable("Getting service from write-action leads to possible deadlock. Service implementation " +
                                 implementation));
        }

        // heavy to prevent storages from flushing and blocking FS
        try (AccessToken ignore = HeavyProcessLatch.INSTANCE.processStarted("Creating service '" + implementation + "'")) {
          Runnable runnable = () -> myInitializedComponentInstance = createAndInitialize(container);
          if (ProgressIndicatorProvider.getGlobalProgressIndicator() != null) {
            ProgressManager.getInstance().executeNonCancelableSection(runnable);
          }
          else {
            runnable.run();
          }
          return myInitializedComponentInstance;
        }
      }
    }

    @NotNull
    private Object createAndInitialize(@NotNull PicoContainer container) {
      Activity activity = StartUpMeasurer.start(Activities.SERVICE, getActivityLevel(container));
      Object instance = getDelegate().getComponentInstance(container);
      if (instance instanceof Disposable) {
        Disposer.register(myComponentManager, (Disposable)instance);
      }

      myComponentManager.initializeComponent(instance, true);
      activity.endWithThreshold(instance.getClass());
      return instance;
    }

    @NotNull
    private synchronized ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        Class<?> implClass;
        try {
          ClassLoader classLoader = myPluginDescriptor.getPluginClassLoader();
          implClass = Class.forName(myDescriptor.getImplementation(), true, classLoader);
        }
        catch (ClassNotFoundException e) {
          throw new PluginException("Failed to load class: " + myDescriptor, e, myPluginDescriptor.getPluginId());
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
      return "ServiceComponentAdapter(descriptor=" + myDescriptor + ", pluginDescriptor=" + myPluginDescriptor + ")";
    }
  }
}