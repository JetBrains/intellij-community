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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class GeneralConfigurable extends ConfigurableBase<GeneralConfigurableUi, XDebuggerGeneralSettings> {
  @Override
  protected XDebuggerGeneralSettings getSettings() {
    return XDebuggerSettingsManager.getInstanceImpl().getGeneralSettings();
  }

  @Override
  protected GeneralConfigurableUi createUi() {
    return new GeneralConfigurableUi();
  }

  @NotNull
  @Override
  public String getId() {
    return "debugger.general";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }
}