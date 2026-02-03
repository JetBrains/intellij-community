package com.intellij.grazie.ide.inspection.auto;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

class GrazieTypedHandler extends TypedHandlerDelegate {

  @Override
  public void newTypingStarted(char c, @NotNull Editor editor, @NotNull DataContext context) {
    ChangeTracker.getInstance(); // initialize the listeners
  }
}
