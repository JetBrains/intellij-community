/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

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