// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

final class NextPrevWordHandler extends EditorActionHandler.ForEachCaret {
  private final boolean myNext;
  private final boolean myWithSelection;
  private final boolean myInDifferentHumpsMode;

  NextPrevWordHandler(boolean next, boolean withSelection, boolean inDifferentHumpsMode) {
    myNext = next;
    myWithSelection = withSelection;
    myInDifferentHumpsMode = inDifferentHumpsMode;
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (EditorUtil.isPasswordEditor(editor)) {
      int selectionStartOffset = caret.getLeadSelectionOffset();
      caret.moveToOffset(myNext ? editor.getDocument().getTextLength() : 0);
      if (myWithSelection) caret.setSelection(selectionStartOffset, caret.getOffset());
    }
    else {
      VisualPosition currentPosition = caret.getVisualPosition();
      if (caret.isAtBidiRunBoundary() && (myNext ^ currentPosition.leansRight)) {
        int selectionStartOffset = caret.getLeadSelectionOffset();
        VisualPosition selectionStartPosition = caret.getLeadSelectionPosition();
        caret.moveToVisualPosition(currentPosition.leanRight(!currentPosition.leansRight));
        if (myWithSelection) {
          caret.setSelection(selectionStartPosition, selectionStartOffset, caret.getVisualPosition(), caret.getOffset());
        }
      }
      else {
        if (myNext ^ caret.isAtRtlLocation()) {
          EditorActionUtil.moveCaretToNextWord(editor, myWithSelection, myInDifferentHumpsMode ^ editor.getSettings().isCamelWords());
        }
        else {
          EditorActionUtil.moveCaretToPreviousWord(editor, myWithSelection, myInDifferentHumpsMode ^ editor.getSettings().isCamelWords());
        }
      }
    }
  }
}
