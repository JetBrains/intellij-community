/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;

public class ToggleColumnModeAction extends ToggleAction implements DumbAware {

  public void setSelected(AnActionEvent e, boolean state) {
    final EditorEx editor = getEditor(e);
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (state) {
      boolean hasSelection = selectionModel.hasSelection();
      int selStart = selectionModel.getSelectionStart();
      int selEnd = selectionModel.getSelectionEnd();
      final CaretModel caretModel = editor.getCaretModel();
      LogicalPosition blockStart = selStart == caretModel.getOffset()
                                   ? caretModel.getLogicalPosition()
                                   : editor.offsetToLogicalPosition(selStart);
      LogicalPosition blockEnd = selEnd == caretModel.getOffset()
                                 ? caretModel.getLogicalPosition()
                                 : editor.offsetToLogicalPosition(selEnd);
      editor.setColumnMode(true);
      if (hasSelection) {
        selectionModel.setBlockSelection(blockStart, blockEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
    else {
      final boolean hasSelection = selectionModel.hasBlockSelection();
      final LogicalPosition blockStart = selectionModel.getBlockStart();
      final LogicalPosition blockEnd = selectionModel.getBlockEnd();

      int selStart = hasSelection && blockStart != null ? editor.logicalPositionToOffset(blockStart) : 0;
      int selEnd = hasSelection && blockEnd != null ? editor.logicalPositionToOffset(blockEnd) : 0;

      editor.setColumnMode(false);
      if (hasSelection) {
        selectionModel.setSelection(selStart, selEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
  }

  public boolean isSelected(AnActionEvent e) {
    final EditorEx ex = getEditor(e);
    return ex != null && ex.isColumnMode();
  }

  private static EditorEx getEditor(AnActionEvent e) {
    return (EditorEx) PlatformDataKeys.EDITOR.getData(e.getDataContext());
  }

  public void update(AnActionEvent e){
    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    } else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }
}
