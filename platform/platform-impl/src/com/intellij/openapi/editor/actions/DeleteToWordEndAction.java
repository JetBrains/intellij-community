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

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 */
public class DeleteToWordEndAction extends TextComponentEditorAction {
  public DeleteToWordEndAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorWriteActionHandler {
    
    private final boolean myNegateCamelMode;

    Handler(boolean negateCamelMode) {
      super(true);
      myNegateCamelMode = negateCamelMode;
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();

      boolean camelMode = editor.getSettings().isCamelWords();
      if (myNegateCamelMode) {
        camelMode = !camelMode;
      }

      if (editor.getSelectionModel().hasSelection()) {
        EditorModificationUtil.deleteSelectedText(editor);
        return;
      }

      deleteToWordEnd(editor, camelMode);
    }
  }

  private static void deleteToWordEnd(Editor editor, boolean camelMode) {
    int startOffset = editor.getCaretModel().getOffset();
    int endOffset = getWordEndOffset(editor, startOffset, camelMode);
    if(endOffset > startOffset) {
      Document document = editor.getDocument();
      document.deleteString(startOffset, endOffset);
    }
  }

  private static int getWordEndOffset(Editor editor, int offset, boolean camelMode) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    if(offset >= document.getTextLength() - 1)
      return offset;
    int newOffset = offset + 1;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if(newOffset > maxOffset) {
      if(lineNumber+1 >= document.getLineCount())
        return offset;
      maxOffset = document.getLineEndOffset(lineNumber+1);
    }
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordEnd(text, newOffset, camelMode) ||
          EditorActionUtil.isWordStart(text, newOffset, camelMode) ||
          EditorActionUtil.isLexemeBoundary(editor, newOffset)) {
        break;
      }
    }
    return newOffset;
  }
}
