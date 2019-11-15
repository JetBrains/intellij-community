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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
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

  @Override
  public void dispose() {
  }
}