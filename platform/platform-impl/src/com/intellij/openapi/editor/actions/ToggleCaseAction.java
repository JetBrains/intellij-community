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
 * Date: May 20, 2002
 * Time: 4:13:37 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class ToggleCaseAction extends TextComponentEditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();

      final int[] starts;
      final int[] ends;
      LogicalPosition blockStart = null;
      LogicalPosition blockEnd = null;

      if (selectionModel.hasBlockSelection()) {
        starts = selectionModel.getBlockSelectionStarts();
        ends = selectionModel.getBlockSelectionEnds();
        blockStart = selectionModel.getBlockStart();
        blockEnd = selectionModel.getBlockEnd();
      }
      else {
        if (!selectionModel.hasSelection()) {
          selectionModel.selectWordAtCaret(true);
        }

        starts = new int[] {selectionModel.getSelectionStart()};
        ends = new int[] {selectionModel.getSelectionEnd()};
      }

      selectionModel.removeBlockSelection();
      selectionModel.removeSelection();

      for (int i = 0; i < starts.length; i++) {
        int startOffset = starts[i];
        int endOffset = ends[i];
        StringBuilder builder = new StringBuilder();
        final String text = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
        toCase(builder, text, true);
        if (text.equals(builder.toString())) {
          toCase(builder, text, false);
        }
        editor.getDocument().replaceString(startOffset, endOffset, builder.toString());
      }

      if (blockStart != null) {
        selectionModel.setBlockSelection(blockStart, blockEnd);
      }
      else {
        selectionModel.setSelection(starts[0], ends[0]);
      }
    }

    private static void toCase(final StringBuilder builder, final String text, final boolean lower ) {
      builder.setLength(0);
      boolean prevIsSlash = false;
      for( int i = 0; i < text.length(); ++i) {
        char c = text.charAt(i);
        if( !prevIsSlash ) {
          c = lower ? Character.toLowerCase(c) : Character.toUpperCase(c);
        }
        prevIsSlash = c == '\\';
        builder.append(c);
      }
    }
  }
}
