// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class ServiceManagerImpl implements Disposable {
  private static final Logger LOG = Logger.getInstance(ServiceManagerImpl.class);

  @ApiStatus.Internal
  public static void processAllDescriptors(@NotNull ComponentManager componentManager, @NotNull Consumer<? super ServiceDescriptor> consumer) {
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getLoadedPlugins()) {
      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)plugin;
      ContainerDescriptor containerDescriptor;
      if (componentManager instanceof Application) {
        containerDescriptor = pluginDescriptor.getApp();
      }
      else if (componentManager instanceof Project) {
        containerDescriptor = pluginDescriptor.getProject();
      }
      else {
        containerDescriptor = pluginDescriptor.getModule();
      }

      containerDescriptor.getServices().forEach(consumer);
    }
  }

  @ApiStatus.Internal
  public static void processProjectDescriptors(@NotNull BiConsumer<? super ServiceDescriptor, ? super PluginDescriptor> consumer) {
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getLoadedPlugins()) {
      for (ServiceDescriptor serviceDescriptor : ((IdeaPluginDescriptorImpl)plugin).getProject().getServices()) {
        consumer.accept(serviceDescriptor, plugin);
      }
    }
  }

  public static void processAllImplementationClasses(@NotNull ComponentManager componentManager,
                                                     @NotNull BiPredicate<? super Class<?>, ? super PluginDescriptor> processor) {
    @SuppressWarnings("unchecked")
    Collection<ComponentAdapter> adapters = componentManager.getPicoContainer().getComponentAdapters();
    if (adapters.isEmpty()) {
      return;
    }

    for (ComponentAdapter o : adapters) {
      Class<?> aClass;
      if (o instanceof ServiceComponentAdapter) {
        ServiceComponentAdapter adapter = (ServiceComponentAdapter)o;
        PluginDescriptor pluginDescriptor = adapter.getPluginDescriptor();
        try {
          // avoid delegation creation & class initialization
          if (adapter.isImplementationClassResolved()) {
            aClass = adapter.getImplementationClass();
          }
          else {
            ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
            aClass = Class.forName(adapter.getDescriptor().getImplementation(), false, classLoader);
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
        PluginId pluginId = o instanceof BaseComponentAdapter ? ((BaseComponentAdapter)o).getPluginId() : null;
        // allow InstanceComponentAdapter without pluginId to test
        if (pluginId != null || o instanceof DefaultPicoContainer.InstanceComponentAdapter) {
          try {
            aClass = o.getComponentImplementation();
          }
          catch (Throwable e) {
            LOG.error(e);
            continue;
          }

          if (!processor.test(aClass, pluginId == null ? null : PluginManagerCore.getPlugin(pluginId))) {
            break;
          }
        }
      }
    }
  }

  @Override
  public void dispose() {
  }
}