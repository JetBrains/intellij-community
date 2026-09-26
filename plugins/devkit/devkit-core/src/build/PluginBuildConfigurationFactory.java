// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level factory that owns one {@link PluginBuildConfiguration} instance per module.
 */
@Service(Service.Level.PROJECT)
public final class PluginBuildConfigurationFactory {
  private final Map<Module, PluginBuildConfiguration> instances = new ConcurrentHashMap<>();

  public static @NotNull PluginBuildConfigurationFactory getInstance(@NotNull Project project) {
    return project.getService(PluginBuildConfigurationFactory.class);
  }

  public @NotNull PluginBuildConfiguration getService(@NotNull Module module) {
    return instances.computeIfAbsent(module, m -> {
      PluginBuildConfiguration configuration = new PluginBuildConfiguration(m);
      Disposer.register(m, () -> instances.remove(m));
      return configuration;
    });
  }
}
