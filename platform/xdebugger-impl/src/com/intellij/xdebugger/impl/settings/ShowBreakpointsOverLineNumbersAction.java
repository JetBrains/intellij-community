// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class ShowBreakpointsOverLineNumbersAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected();
  }

  public static boolean isSelected() {
    return UISettings.getInstance().getShowBreakpointsOverLineNumbers()
           && EditorSettingsExternalizable.getInstance().isLineNumbersShown();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    UISettings.getInstance().setShowBreakpointsOverLineNumbers(state);
    EditorFactory.getInstance().refreshAllEditors();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(ExperimentalUI.isNewUI());
    if (!EditorSettingsExternalizable.getInstance().isLineNumbersShown()) {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
