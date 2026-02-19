// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import org.jetbrains.annotations.NotNull;

/**
 * Action that toggles {@code 'show soft wraps at editor'} option and is expected to be used at various menus.
 */
public class ToggleUseSoftWrapsMenuAction extends AbstractToggleUseSoftWrapsAction {

  public ToggleUseSoftWrapsMenuAction() {
    super(SoftWrapAppliancePlaces.MAIN_EDITOR, false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (!e.isFromActionToolbar()) {
      e.getPresentation().setIcon(null);
    }
    if (ActionPlaces.UNKNOWN.equals(e.getPlace())) {
      e.getPresentation().setText(ActionsBundle.messagePointer("action.EditorGutterToggleLocalSoftWraps.gutterText"));
    }
    e.getPresentation().setEnabled(getEditor(e) != null);
  }
}
