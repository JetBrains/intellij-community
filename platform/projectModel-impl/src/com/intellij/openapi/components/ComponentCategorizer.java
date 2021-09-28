// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComponentCategorizer {
  private ComponentCategorizer() {
  }

  public static @NotNull SettingsCategory getCategory(@NotNull PersistentStateComponent<?> component) {
    PluginId pluginId = getPluginId(component);
    if (pluginId != null) {
      getPluginCategory(component.getClass(), pluginId);
    }
    return getFromAnnotationOrDefault(component.getClass(), SettingsCategory.OTHER);
  }

  @SuppressWarnings("rawtypes")
  public static @NotNull SettingsCategory getPluginCategory(@NotNull Class<? extends PersistentStateComponent> componentClass,
                                                            @NotNull PluginId pluginId) {
    IdeaPluginDescriptor pluginDescriptor = PluginManager.getInstance().findEnabledPlugin(pluginId);
    if (pluginDescriptor != null) {
      if (pluginDescriptor.isBundled()) {
        return getFromAnnotationOrDefault(componentClass, SettingsCategory.PLUGINS);
      }
    }
    return SettingsCategory.PLUGINS;
  }

  @SuppressWarnings("rawtypes")
  private static @NotNull SettingsCategory getFromAnnotationOrDefault(@NotNull Class<? extends PersistentStateComponent> componentClass,
                                                                      @NotNull SettingsCategory defaultCategory) {
    State state = componentClass.getAnnotation(State.class);
    return state != null && !SettingsCategory.OTHER.equals(state.category()) ? state.category() : defaultCategory;
  }

  public static @Nullable PluginId getPluginId(@NotNull PersistentStateComponent<?> component) {
    return PluginManager.getPluginByClassName(component.getClass().getName());
  }
}
