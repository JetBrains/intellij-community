// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.EventObject;

public final class ErrorStripeEvent extends EventObject {
  private final MouseEvent myMouseEvent;
  private final RangeHighlighter myHighlighter;

  public ErrorStripeEvent(@NotNull Editor editor, @Nullable MouseEvent mouseEvent, @NotNull RangeHighlighter highlighter) {
    super(editor);
    myMouseEvent = mouseEvent;
    myHighlighter = highlighter;
  }

  public @NotNull Editor getEditor() {
    return (Editor)getSource();
  }

  public @Nullable MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public @NotNull RangeHighlighter getHighlighter() {
    return myHighlighter;
  }
}
