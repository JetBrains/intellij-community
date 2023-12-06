// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TextComponentSelectionModel implements SelectionModel {
  private final TextComponentEditor myEditor;

  TextComponentSelectionModel(@NotNull TextComponentEditorImpl textComponentEditor) {
    myEditor = textComponentEditor;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  @Override
  public @Nullable VisualPosition getSelectionStartPosition() {
    return null;
  }

  @Override
  public @Nullable VisualPosition getSelectionEndPosition() {
    return null;
  }

  @Override
  public @Nullable VisualPosition getLeadSelectionPosition() {
    return null;
  }

  @Override
  public void addSelectionListener(final @NotNull SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeSelectionListener(final @NotNull SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void copySelectionToClipboard() {
    EditorCopyPasteHelper.getInstance().copySelectionToClipboard(myEditor);
  }

  @Override
  public void setBlockSelection(final @NotNull LogicalPosition blockStart, final @NotNull LogicalPosition blockEnd) {
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
