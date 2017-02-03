/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SettingsDialogFactory {
  public static SettingsDialogFactory getInstance() {
    return ServiceManager.getService(SettingsDialogFactory.class);
  }

  public DialogWrapper create(Project project, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton){
    return new SettingsDialog(project, key, configurable, showApplyButton, showResetButton);
  }

  public DialogWrapper create(@NotNull Component parent, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton){
    return new SettingsDialog(parent, key, configurable, showApplyButton, showResetButton);
  }

  public DialogWrapper create(@NotNull Project project, @NotNull ConfigurableGroup[] groups, Configurable configurable, String filter){
    return new SettingsDialog(project, groups, configurable, filter);
  }
}