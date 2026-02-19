// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ToggleUseSoftWrapsInPreviewAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null || editor.getEditorKind() != EditorKind.PREVIEW) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor != null && editor.getSettings().isUseSoftWraps();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editor, SoftWrapAppliancePlaces.PREVIEW, state);
  }
}
