// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import org.jetbrains.annotations.NotNull;

/**
 * Allows customizing actions during the {@link ActionManager} service initialization.
 * <p>
 * Register in {@code com.intellij.actionConfigurationCustomizer} extension point.
 *
 * @see DynamicActionConfigurationCustomizer
 */
public interface ActionConfigurationCustomizer {

  /**
   * @param actionManager action manager being initialized
   */
  void customize(@NotNull ActionManager actionManager);
}
