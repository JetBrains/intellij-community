// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TextComponentSelectionModel implements SelectionModel {
  private final TextComponentEditor myEditor;

  TextComponentSelectionModel(@NotNull TextComponentEditorImpl textComponentEditor) {
    myEditor = textComponentEditor;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Nullable
  @Override
  public VisualPosition getSelectionStartPosition() {
    return null;
  }

  @Nullable
  @Override
  public VisualPosition getSelectionEndPosition() {
    return null;
  }

  @Nullable
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return null;
  }

  @Override
  public void addSelectionListener(@NotNull final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeSelectionListener(@NotNull final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void copySelectionToClipboard() {
    EditorCopyPasteHelper.getInstance().copySelectionToClipboard(myEditor);
  }

  @Override
  public void setBlockSelection(@NotNull final LogicalPosition blockStart, @NotNull final LogicalPosition blockEnd) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int @NotNull [] getBlockSelectionStarts() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int @NotNull [] getBlockSelectionEnds() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TextAttributes getTextAttributes() {
    return null;
  }
}
