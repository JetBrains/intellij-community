// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;


final class EditorCopyPastProvider implements CutProvider, CopyProvider, PasteProvider, DeleteProvider, DumbAware {

  private final Editor editor;

  EditorCopyPastProvider(Editor editor) {
    this.editor = editor;
  }

  @SuppressWarnings("RedundantMethodOverride")
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    executeAction(IdeActions.ACTION_EDITOR_COPY, dataContext);
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    // DBE-13853 Hide actions for selection (e.g. Copy, Paste) in editor context menu that was invoked outside of selection
    Caret caret = dataContext.getData(CommonDataKeys.CARET);
    return caret != null
           ? EditorUtil.isCaretInsideSelection(caret)
           : editor.getSelectionModel().hasSelection(true);
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
    executeAction(IdeActions.ACTION_EDITOR_CUT, dataContext);
  }

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return !editor.isViewer();
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    // DBE-13853 Hide actions for selection (e.g. Copy, Paste) in editor context menu that was invoked outside of selection
    Caret caret = dataContext.getData(CommonDataKeys.CARET);
    return isCutEnabled(dataContext) &&
           (caret != null
            ? EditorUtil.isCaretInsideSelection(caret)
            : editor.getSelectionModel().hasSelection(true));
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    executeAction(IdeActions.ACTION_EDITOR_PASTE, dataContext);
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    // Copy of isPasteEnabled. See interface method javadoc.
    return !editor.isViewer();
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return !editor.isViewer();
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    executeAction(IdeActions.ACTION_EDITOR_DELETE, dataContext);
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return !editor.isViewer();
  }

  private void executeAction(@NotNull String actionId, @NotNull DataContext dataContext) {
    EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
    if (action != null) {
      action.actionPerformed(editor, dataContext);
    }
  }
}
