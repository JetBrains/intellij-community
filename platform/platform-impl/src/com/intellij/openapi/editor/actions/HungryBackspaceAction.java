// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Works like a usual backspace except the situation when the caret is located after white space - all white space symbols
 * (white spaces, tabulations, line feeds) are removed then.
 */
@ApiStatus.Internal
public final class HungryBackspaceAction extends TextComponentEditorAction {

  public HungryBackspaceAction() {
    super(new Handler());
  }
  
  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
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
