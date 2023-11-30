// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public final class DeleteAction extends EditorAction {
  public DeleteAction() {
    super(new Handler());
  }

  public static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      if (editor.getInlayModel().hasInlineElementAt(editor.getCaretModel().getVisualPosition())) {
        editor.getCaretModel().moveCaretRelatively(1, 0, false, false, EditorUtil.isCurrentCaretPrimary(editor));
      }
      else {
        deleteCharAtCaret(editor);
      }
    }
  }

  private static int getCaretLineLength(Editor editor) {
    Document document = editor.getDocument();
    if (document.getLineCount() == 0) {
      return 0;
    }
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      return 0;
    }
    return document.getLineEndOffset(lineNumber) - document.getLineStartOffset(lineNumber);
  }

  private static int getCaretLineStart(Editor editor) {
    Document document = editor.getDocument();
    if (document.getLineCount() == 0) {
      return 0;
    }
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      return document.getLineStartOffset(document.getLineCount() - 1);
    }
    return document.getLineStartOffset(lineNumber);
  }

  public static void deleteCharAtCaret(Editor editor) {
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int afterLineEnd = EditorModificationUtil.calcAfterLineEnd(editor);
    Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if (afterLineEnd < 0
        // There is a possible case that caret is located right before the soft wrap position at the last logical line
        // (popular use case with the soft wraps at the commit message dialog).
        || (offset < document.getTextLength() - 1 && editor.getSoftWrapModel().getSoftWrap(offset) != null)) {
      FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
      if (region != null && region.shouldNeverExpand()) {
        document.deleteString(region.getStartOffset(), region.getEndOffset());
        editor.getCaretModel().moveToOffset(region.getStartOffset());
      }
      else {
        document.deleteString(offset, DocumentUtil.getNextCodePointOffset(document, offset));
      }
      return;
    }

    if (lineNumber + 1 >= document.getLineCount()) return;

    // Do not group delete newline and other deletions.
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.setCurrentCommandGroupId(null);

    int nextLineStart = document.getLineStartOffset(lineNumber + 1);
    int nextLineEnd = document.getLineEndOffset(lineNumber + 1);
    if (nextLineEnd - nextLineStart > 0) {
      StringBuilder buf = new StringBuilder();
      StringUtil.repeatSymbol(buf, ' ', afterLineEnd);
      document.insertString(getCaretLineStart(editor) + getCaretLineLength(editor), buf.toString());
      nextLineStart = document.getLineStartOffset(lineNumber + 1);
    }
    int thisLineEnd = document.getLineEndOffset(lineNumber);
    document.deleteString(thisLineEnd, nextLineStart);
  }
}
