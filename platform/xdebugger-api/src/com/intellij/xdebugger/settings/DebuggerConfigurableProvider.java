// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Allows to register custom debugger-related settings
 * @see DebuggerSettingsCategory
 */
public abstract class DebuggerConfigurableProvider {
  public static final ExtensionPointName<DebuggerConfigurableProvider> EXTENSION_POINT = ExtensionPointName.create("com.intellij.xdebugger.configurableProvider");

  @NotNull
  public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    return Collections.emptyList();
  }

  /**
   * General settings of category were applied
   */
  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
  }

  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    return false;
  }
}