// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.impl.view.EditorView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


final class EditorTextDrawingCallback implements TextDrawingCallback {

  private final EditorView editorView;

  EditorTextDrawingCallback(EditorView editorView) {
    this.editorView = editorView;
  }

  @Override
  public void drawChars(
    @NotNull Graphics g,
    char @NotNull [] data,
    int start,
    int end,
    int x,
    int y,
    @NotNull Color color,
    @NotNull FontInfo fontInfo
  ) {
    editorView.drawChars(g, data, start, end, x, y, color, fontInfo);
  }
}
