// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SelectionDeleteLoggingHandlerBase extends EditorActionHandler {
  private final @Nullable EditorActionHandler myOriginalHandler;
  private final @NotNull TypingEventsLogger.SelectionDeleteAction myDeleteAction;

  SelectionDeleteLoggingHandlerBase(@Nullable EditorActionHandler originalHandler,
                                    @NotNull TypingEventsLogger.SelectionDeleteAction deleteAction) {
    myOriginalHandler = originalHandler;
    myDeleteAction = deleteAction;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (dataContext != null) {
      if (caret == null) {
        if (editor.getSelectionModel().hasSelection(true)) {
          CaretAction action = c -> logSelection(editor, c, dataContext);
          editor.getCaretModel().runForEachCaret(action);
        }
      }
      else {
        logSelection(editor, caret, dataContext);
      }
    }

    if (myOriginalHandler != null) {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return myOriginalHandler == null || myOriginalHandler.isEnabled(editor, caret, dataContext);
  }

  @Override
  public boolean executeInCommand(@NotNull Editor editor, DataContext dataContext) {
    return myOriginalHandler == null || myOriginalHandler.executeInCommand(editor, dataContext);
  }

  @Override
  public DocCommandGroupId getCommandGroupId(@NotNull Editor editor) {
    return myOriginalHandler == null ? super.getCommandGroupId(editor) : myOriginalHandler.getCommandGroupId(editor);
  }

  @Override
  public boolean runForAllCarets() {
    return myOriginalHandler != null && myOriginalHandler.runForAllCarets();
  }

  @Override
  public boolean reverseCaretOrder() {
    return myOriginalHandler != null && myOriginalHandler.reverseCaretOrder();
  }

  private void logSelection(@NotNull Editor editor, @NotNull Caret caret, @NotNull DataContext dataContext) {
    if (!caret.hasSelection()) return;
    int selectionLength = caret.getSelectionEnd() - caret.getSelectionStart();
    TypingEventsLogger.logSelectionDeleted(editor,
                                           caretDataContext(dataContext, caret),
                                           selectionLength,
                                           myDeleteAction);
  }
}
