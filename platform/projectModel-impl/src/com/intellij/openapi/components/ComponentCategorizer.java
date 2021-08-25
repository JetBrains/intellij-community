// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComponentCategorizer {
  private ComponentCategorizer() {
  }

  public static @Nullable ComponentCategory getCategory(@NotNull PersistentStateComponent<?> component) {
    PluginId pluginId = getPluginId(component);
    if (pluginId != null) {
      return ComponentCategory.PLUGINS;
    }
    State state = component.getClass().getAnnotation(State.class);
    return state != null ? state.category() : ComponentCategory.OTHER;
  }

  public static @Nullable PluginId getPluginId(@NotNull PersistentStateComponent<?> component) {
    return PluginManager.getPluginByClassName(component.getClass().getName());
  }
}
