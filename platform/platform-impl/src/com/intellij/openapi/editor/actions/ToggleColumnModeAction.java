// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class ToggleColumnModeAction extends ToggleAction implements DumbAware, LightEditCompatible, ActionRemoteBehaviorSpecification.Frontend {
  public ToggleColumnModeAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final EditorEx editor = getEditor(e);
    final SelectionModel selectionModel = editor.getSelectionModel();
    final CaretModel caretModel = editor.getCaretModel();
    if (state) {
      caretModel.removeSecondaryCarets();
      boolean hasSelection = selectionModel.hasSelection();
      int selStart = selectionModel.getSelectionStart();
      int selEnd = selectionModel.getSelectionEnd();
      LogicalPosition blockStart, blockEnd;
      if (caretModel.supportsMultipleCarets()) {
        LogicalPosition logicalSelStart = editor.offsetToLogicalPosition(selStart);
        LogicalPosition logicalSelEnd = editor.offsetToLogicalPosition(selEnd);
        int caretOffset = caretModel.getOffset();
        blockStart = selStart == caretOffset ? logicalSelEnd : logicalSelStart;
        blockEnd = selStart == caretOffset ? logicalSelStart : logicalSelEnd;
      }
      else {
        blockStart = selStart == caretModel.getOffset()
                                     ? caretModel.getLogicalPosition()
                                     : editor.offsetToLogicalPosition(selStart);
        blockEnd = selEnd == caretModel.getOffset()
                                   ? caretModel.getLogicalPosition()
                                   : editor.offsetToLogicalPosition(selEnd);
      }
      editor.setColumnMode(true);
      if (hasSelection) {
        selectionModel.setBlockSelection(blockStart, blockEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
    else {
      boolean hasSelection = false;
      int selStart = 0;
      int selEnd = 0;

      if (caretModel.supportsMultipleCarets()) {
        hasSelection = true;
        List<Caret> allCarets = caretModel.getAllCarets();
        Caret fromCaret = allCarets.get(0);
        Caret toCaret = allCarets.get(allCarets.size() - 1);
        if (fromCaret == caretModel.getPrimaryCaret()) {
          Caret tmp = fromCaret;
          fromCaret = toCaret;
          toCaret = tmp;
        }
        selStart = fromCaret.getLeadSelectionOffset();
        selEnd = toCaret.getSelectionStart() == toCaret.getLeadSelectionOffset() ? toCaret.getSelectionEnd() : toCaret.getSelectionStart();
      }

      editor.setColumnMode(false);
      caretModel.removeSecondaryCarets();
      if (hasSelection) {
        selectionModel.setSelection(selStart, selEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    final EditorEx ex = getEditor(e);
    return ex != null && ex.isColumnMode();
  }

  private static EditorEx getEditor(@NotNull AnActionEvent e) {
    return (EditorEx)e.getData(CommonDataKeys.EDITOR);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EditorEx editor = getEditor(e);
    if (editor == null || editor.isOneLineMode()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(true);
      super.update(e);
    }
  }
}
