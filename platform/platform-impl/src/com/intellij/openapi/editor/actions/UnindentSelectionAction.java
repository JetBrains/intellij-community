// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class UnindentSelectionAction extends EditorAction {
  public UnindentSelectionAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      unindentSelection(editor, project);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isViewer() && !editor.isOneLineMode() && !((EditorEx)editor).isEmbeddedIntoDialogWrapper();
    }
  }

  private static void unindentSelection(Editor editor, Project project) {
    int oldSelectionStart = editor.getSelectionModel().getSelectionStart();
    int oldSelectionEnd = editor.getSelectionModel().getSelectionEnd();
    if(!editor.getSelectionModel().hasSelection()) {
      oldSelectionStart = editor.getCaretModel().getOffset();
      oldSelectionEnd = oldSelectionStart;
    }

    Document document = editor.getDocument();
    int startIndex = document.getLineNumber(oldSelectionStart);
    if(startIndex == -1) {
      startIndex = document.getLineCount() - 1;
    }
    int endIndex = document.getLineNumber(oldSelectionEnd);
    if(endIndex > 0 && document.getLineStartOffset(endIndex) == oldSelectionEnd && endIndex > startIndex) {
      endIndex --;
    }
    if(endIndex == -1) {
      endIndex = document.getLineCount() - 1;
    }

    if (startIndex < 0 || endIndex < 0) return;

    int blockIndent = CodeStyle.getIndentOptions(project, document).INDENT_SIZE;
    IndentSelectionAction.doIndent(endIndex, startIndex, document, project, editor, -blockIndent);
  }
}
