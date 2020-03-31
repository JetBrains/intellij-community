// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextEndAction extends TextComponentEditorAction {
  public TextEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      editor.getCaretModel().removeSecondaryCarets();
      int offset = editor.getDocument().getTextLength();
      if (editor instanceof EditorImpl) {
        editor.getCaretModel().moveToLogicalPosition(editor.offsetToLogicalPosition(offset).leanForward(true));
      }
      else {
        editor.getCaretModel().moveToOffset(offset);
      }
      editor.getSelectionModel().removeSelection();

      ScrollingModel scrollingModel = editor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.CENTER);
      scrollingModel.enableAnimation();

      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        IdeDocumentHistory instance = IdeDocumentHistory.getInstance(project);
        if (instance != null) {
          instance.includeCurrentCommandAsNavigation();
        }
      }
    }
  }
}
