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
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Works like a usual backspace except the situation when the caret is located after white space - all white space symbols
 * (white spaces, tabulations, line feeds) are removed then.
 * 
 * @author Denis Zhdanov
 * @since 6/27/12 4:10 PM
 */
public class HungryBackspaceAction extends TextComponentEditorAction {

  public HungryBackspaceAction() {
    super(new Handler());
  }
  
  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, Caret caret, DataContext dataContext) {
      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (caretOffset < 1) {
        return;
      }

      final SelectionModel selectionModel = editor.getSelectionModel();
      final CharSequence text = document.getCharsSequence();
      final char c = text.charAt(caretOffset - 1);
      if (!selectionModel.hasSelection() && StringUtil.isWhiteSpace(c)) {
        int startOffset = CharArrayUtil.shiftBackward(text, caretOffset - 2, "\t \n") + 1;
        document.deleteString(startOffset, caretOffset);
      }
      else {
        final EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
        handler.execute(editor, caret, dataContext);
      }
    }
  }
}
