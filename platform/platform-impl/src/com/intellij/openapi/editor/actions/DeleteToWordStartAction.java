/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.text.CharArrayUtil;

public class DeleteToWordStartAction extends TextComponentEditorAction {
  public DeleteToWordStartAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      deleteToWordStart(editor);
    }
  }

  private static void deleteToWordStart(Editor editor) {
    final CaretModel caretModel = editor.getCaretModel();
    int endOffset = caretModel.getOffset();
    
    // The logic is as follows:
    //   1. Check are there white-space symbols starting at the current caret position going backwards. Delete them if any;
    //   2. Otherwise locate previous word start and delete the text up to it;
    // Example:
    //    'test string    <caret>' -> 'test string<caret>'
    //    'test string<caret>'     -> 'test <caret>'

    Document document = editor.getDocument();
    final SelectionModel selectionModel = editor.getSelectionModel();
    int startOffset = -1;
    if (!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) {
      int i = CharArrayUtil.shiftBackward(document.getCharsSequence(), Math.max(0, endOffset - 1), " \t\n");
      if (i >= 0 && i < endOffset - 1) {
        startOffset = i + 1; // We need offset of the first white space symbol, not offset of the last non-white space symbol before it.
      }
    }

    if (startOffset < 0) {
      EditorActionUtil.moveCaretToPreviousWord(editor, false);
      startOffset = caretModel.getOffset();
    }
    
    document.deleteString(startOffset, endOffset);
  }
}
