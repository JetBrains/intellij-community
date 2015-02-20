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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:40:40 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;

import java.util.List;

public class ToggleColumnModeAction extends ToggleAction implements DumbAware {

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
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
  public boolean isSelected(AnActionEvent e) {
    final EditorEx ex = getEditor(e);
    return ex != null && ex.isColumnMode();
  }

  private static EditorEx getEditor(AnActionEvent e) {
    return (EditorEx) CommonDataKeys.EDITOR.getData(e.getDataContext());
  }

  @Override
  public void update(AnActionEvent e){
    EditorEx editor = getEditor(e);
    if (editor == null || editor.isOneLineMode()) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    } else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }
}
