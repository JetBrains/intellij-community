// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PageUpWithSelectionAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!editor.getCaretModel().supportsMultipleCarets()) {
        EditorActionUtil.moveCaretPageUp(editor, true);
        return;
      }
      if (editor.isColumnMode()) {
        int lines = editor.getScrollingModel().getVisibleArea().height / editor.getLineHeight();
        CloneCaretActionHandler handler = new CloneCaretActionHandler(true);
        for (int i = 0; i < lines; i++) {
          handler.execute(editor, caret, dataContext);
          handler.setRepeatedInvocation(true);
        }
      }
      else {
        if (caret == null) {
          editor.getCaretModel().runForEachCaret(__ -> EditorActionUtil.moveCaretPageUp(editor, true));
        }
        else {
          // assuming caret is equal to CaretModel.getCurrentCaret()
          EditorActionUtil.moveCaretPageUp(editor, true);
        }
      }
    }
  }

  public PageUpWithSelectionAction() {
    super(new Handler());
  }
}
