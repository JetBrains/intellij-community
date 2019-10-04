// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class SettingsDialogFactory {
  public static SettingsDialogFactory getInstance() {
    return ServiceManager.getService(SettingsDialogFactory.class);
  }

  @NotNull
  public DialogWrapper create(Project project, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton){
    return new SettingsDialog(project, key, configurable, showApplyButton, showResetButton);
  }

  @NotNull
  public DialogWrapper create(@NotNull Component parent, String key, @NotNull Configurable configurable, boolean showApplyButton, boolean showResetButton){
    return new SettingsDialog(parent, key, configurable, showApplyButton, showResetButton);
  }

  @NotNull
  public DialogWrapper create(@NotNull Project project, @NotNull ConfigurableGroup[] groups, Configurable configurable, String filter){
    return create(project, Arrays.asList(groups), configurable, filter);
  }

  @NotNull
  public DialogWrapper create(@NotNull Project project, @NotNull List<? extends ConfigurableGroup> groups, @Nullable Configurable configurable, @Nullable String filter) {
    return new SettingsDialog(project, groups, configurable, filter);
  }
}