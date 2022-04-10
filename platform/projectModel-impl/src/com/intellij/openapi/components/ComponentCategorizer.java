// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ComponentCategorizer {
  private ComponentCategorizer() {
  }

  public static @NotNull SettingsCategory getCategory(@NotNull PersistentStateComponent<?> component) {
    return getCategory(component.getClass());
  }

  @SuppressWarnings("rawtypes")
  public static @NotNull SettingsCategory getCategory(@NotNull Class<? extends PersistentStateComponent> componentClass) {
    PluginDescriptor plugin = PluginManager.getPluginByClass(componentClass);
    if (plugin != null) {
      getPluginCategory(componentClass, plugin);
    }
    return getFromAnnotationOrDefault(componentClass, SettingsCategory.OTHER);
  }

  @SuppressWarnings("rawtypes")
  public static @NotNull SettingsCategory getPluginCategory(@NotNull Class<? extends PersistentStateComponent> componentClass,
                                                            @NotNull PluginDescriptor plugin) {
    return plugin.isBundled() ? getFromAnnotationOrDefault(componentClass, SettingsCategory.PLUGINS) : SettingsCategory.PLUGINS;
  }

  @SuppressWarnings("rawtypes")
  private static @NotNull SettingsCategory getFromAnnotationOrDefault(@NotNull Class<? extends PersistentStateComponent> componentClass,
                                                                      @NotNull SettingsCategory defaultCategory) {
    State state = componentClass.getAnnotation(State.class);
    return state != null && !SettingsCategory.OTHER.equals(state.category()) ? state.category() : defaultCategory;
  }

  public static @Nullable PluginDescriptor getPlugin(@NotNull PersistentStateComponent<?> component) {
    return PluginManager.getPluginByClass(component.getClass());
  }
}
