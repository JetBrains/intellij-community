// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class BooleanPropertyToggleAction extends ToggleAction implements DumbAware {
  public BooleanPropertyToggleAction() {
  }

  public BooleanPropertyToggleAction(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  public BooleanPropertyToggleAction(@NotNull Supplier<String> dynamicText,
                                     @NotNull Supplier<String> dynamicDescription,
                                     @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  protected abstract VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty();

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties == null || !properties.exists(getProperty())) return false;
    return properties.get(getProperty());
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties != null && properties.exists(getProperty())) {
      properties.set(getProperty(), state);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setEnabledAndVisible(properties != null && properties.exists(getProperty()));

    super.update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}