// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import org.jetbrains.annotations.NotNull;

/**
 * Allows registering and unregistering actions when a plugin is loaded/unloaded.
 * <p>
 * Register in {@code com.intellij.dynamicActionConfigurationCustomizer} extension point.
 *
 * @see ActionConfigurationCustomizer
 */
public interface DynamicActionConfigurationCustomizer {

  /**
   * Called during {@link ActionManager} initialization and when this extension is added.
   */
  void registerActions(@NotNull ActionManager actionManager);

  /**
   * Called when this extension is removed.
   */
  void unregisterActions(@NotNull ActionManager actionManager);
}
