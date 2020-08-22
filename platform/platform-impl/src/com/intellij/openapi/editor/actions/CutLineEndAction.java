// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CutLineEndAction extends TextComponentEditorAction {
  public CutLineEndAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler {
    private Handler() {
      super(false);
    }

    @Override
    public void executeWriteAction(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (caret == null && editor.getCaretModel().getCaretCount() > 1) {
        editor.getCaretModel().runForEachCaret(c -> c.setSelection(c.getOffset(), getEndOffset(c)));
        // We don't support kill-ring operations for multiple carets currently
        EditorCopyPasteHelper.getInstance().copySelectionToClipboard(editor);
        EditorModificationUtil.deleteSelectedTextForAllCarets(editor);
      }
      else {
        if (caret == null) {
          caret = editor.getCaretModel().getCurrentCaret();
        }
        int startOffset = caret.getOffset();
        int endOffset = getEndOffset(caret);
        KillRingUtil.cut(editor, startOffset, endOffset);
        // in case the caret was in the virtual space, we force it to go back to the real offset
        caret.moveToOffset(startOffset);
      }
    }

    private static int getEndOffset(@NotNull Caret caret) {
      Document document = caret.getEditor().getDocument();
      int startOffset = caret.getOffset();
      int endOffset = DocumentUtil.getLineEndOffset(startOffset, document);
      if (endOffset < document.getTextLength() &&
          CharArrayUtil.isEmptyOrSpaces(document.getImmutableCharSequence(), startOffset, endOffset)) {
        endOffset++;
      }
      return endOffset;
    }
  }
}
