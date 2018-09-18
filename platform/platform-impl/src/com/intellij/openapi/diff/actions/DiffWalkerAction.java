// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public abstract class DiffWalkerAction extends AnAction implements DumbAware {
  protected DiffWalkerAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    if (side == null) return;
    int line = getLineNumberToGo(side);
    Editor editor = side.getEditor();
    if (line >= 0 && editor != null) {
      LogicalPosition pos = new LogicalPosition(line, 0);
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToLogicalPosition(pos);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    Presentation presentation = event.getPresentation();
    if (side == null) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(getLineNumberToGo(side) >= 0 || event.getInputEvent() instanceof KeyEvent);
    }
  }

  protected abstract int getLineNumberToGo(FocusDiffSide side);
}
