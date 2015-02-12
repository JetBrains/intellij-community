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
 * Date: May 13, 2002
 * Time: 8:26:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.ui.MacUIUtil;
import org.jetbrains.annotations.NotNull;

public class BackspaceAction extends EditorAction {
  public BackspaceAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    private Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      MacUIUtil.hideCursor();
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      if (editor instanceof EditorWindow) {
        // manipulate actual document/editor instead of injected
        // since the latter have trouble finding the right location of caret movement in the case of multi-shred injected fragments
        editor = ((EditorWindow)editor).getDelegate();
      }
      doBackSpaceAtCaret(editor);
    }
  }

  private static void doBackSpaceAtCaret(@NotNull Editor editor) {
    if(editor.getSelectionModel().hasSelection()) {
      EditorModificationUtil.deleteSelectedText(editor);
      return;
    }

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int colNumber = editor.getCaretModel().getLogicalPosition().column;
    Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if(colNumber > 0) {
      if(EditorModificationUtil.calcAfterLineEnd(editor) > 0) {
        int columnShift = -1;
        editor.getCaretModel().moveCaretRelatively(columnShift, 0, false, false, true);
      }
      else {
        EditorModificationUtil.scrollToCaret(editor);
        editor.getSelectionModel().removeSelection();

        FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
        if (region != null && region.shouldNeverExpand()) {
          document.deleteString(region.getStartOffset(), region.getEndOffset());
          editor.getCaretModel().moveToOffset(region.getStartOffset());
        }
        else {
          document.deleteString(offset - 1, offset);
          editor.getCaretModel().moveToOffset(offset - 1, true);
        }
      }
    }
    else if(lineNumber > 0) {
      int separatorLength = document.getLineSeparatorLength(lineNumber - 1);
      int lineEnd = document.getLineEndOffset(lineNumber - 1) + separatorLength;
      document.deleteString(lineEnd - separatorLength, lineEnd);
      editor.getCaretModel().moveToOffset(lineEnd - separatorLength);
      EditorModificationUtil.scrollToCaret(editor);
      editor.getSelectionModel().removeSelection();
      // Do not group delete newline and other deletions.
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.setCurrentCommandGroupId(null);
    }
  }
}
