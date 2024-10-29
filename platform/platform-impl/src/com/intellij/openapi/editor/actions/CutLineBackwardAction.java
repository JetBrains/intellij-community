// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for emacs 'reverse-kill-line' action, i.e.
 * <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Killing-by-Lines.html">'kill-line' action</a>
 * with negative argument.
 */
@ApiStatus.Internal
public final class CutLineBackwardAction extends TextComponentEditorAction {

  public CutLineBackwardAction() {
    super(new Handler());
  }

  static final class Handler extends EditorWriteActionHandler {
    
    @Override
    public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final Document document = editor.getDocument();
      int caretOffset = editor.getCaretModel().getOffset();
      if (caretOffset <= 0) {
        return;
      }
      
      // The main idea is to kill everything between the current line start and caret and the whole previous line.
      
      final int caretLine = document.getLineNumber(caretOffset);
      int start;
      
      if (caretLine <= 0) {
        start = 0;
      }
      else {
        start = document.getLineStartOffset(caretLine - 1);
      }
      KillRingUtil.cut(editor, start, caretOffset);
    }
  }
}
