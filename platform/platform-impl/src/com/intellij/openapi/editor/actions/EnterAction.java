// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.LatencyAwareEditorAction;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class EnterAction extends EditorAction implements LatencyAwareEditorAction {
  public EnterAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (IntentionPreviewUtils.getPreviewEditor() != editor) {
        CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.command.name"));
      }
      insertNewLineAtCaret(editor);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isOneLineMode();
    }
  }

  public static void insertNewLineAtCaret(Editor editor) {
    EditorUIUtil.hideCursorInEditor(editor);
    Document document = editor.getDocument();
    if(!editor.isInsertMode()) {
      int caretLine = editor.getCaretModel().getLogicalPosition().line;
      int lineCount = document.getLineCount();
      if(caretLine < lineCount) {
        if (caretLine == lineCount - 1) {
          document.insertString(document.getTextLength(), "\n");
        }
        LogicalPosition pos = new LogicalPosition(caretLine + 1, 0);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getSelectionModel().removeSelection();
        EditorModificationUtil.scrollToCaret(editor);
      }
      return;
    }
    EditorModificationUtil.deleteSelectedText(editor);
    // Smart indenting here:
    CharSequence text = document.getCharsSequence();
    int caretOffset = editor.getCaretModel().getOffset();
    int lineStartOffset = DocumentUtil.getLineStartOffset(caretOffset, document);
    int lineStartWsEndOffset = CharArrayUtil.shiftForward(text, lineStartOffset, " \t");
    String s = "\n" + text.subSequence(lineStartOffset, Math.min(caretOffset, lineStartWsEndOffset));
    document.insertString(caretOffset, s);
    editor.getCaretModel().moveToOffset(caretOffset + s.length());
    EditorModificationUtil.scrollToCaret(editor);
    editor.getSelectionModel().removeSelection();
  }
}
