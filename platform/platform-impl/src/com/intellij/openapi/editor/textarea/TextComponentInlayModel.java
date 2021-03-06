// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

final class TextComponentInlayModel implements InlayModel {
  @Nullable
  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                           boolean relatesToPrecedingText,
                                                                           @NotNull T renderer) {
    return null;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                                     boolean relatesToPrecedingText,
                                                                                     int priority,
                                                                                     @NotNull T renderer) {
    return null;
  }

  @Nullable
  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                          boolean relatesToPrecedingText,
                                                                          boolean showAbove,
                                                                          int priority,
                                                                          @NotNull T renderer) {
    return null;
  }

  @Nullable
  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addAfterLineEndElement(int offset,
                                                                                 boolean relatesToPrecedingText,
                                                                                 @NotNull T renderer) {
    return null;
  }

  @NotNull
  @Override
  public List<Inlay<?>> getInlineElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Inlay<?>> getBlockElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Inlay<?>> getBlockElementsForVisualLine(int visualLine, boolean above) {
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    return false;
  }

  @Nullable
  @Override
  public Inlay<?> getInlineElementAt(@NotNull VisualPosition visualPosition) {
    return null;
  }

  @Nullable
  @Override
  public Inlay<?> getElementAt(@NotNull Point point) {
    return null;
  }

  @NotNull
  @Override
  public List<Inlay<?>> getAfterLineEndElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Inlay<?>> getAfterLineEndElementsForLogicalLine(int logicalLine) {
    return Collections.emptyList();
  }

  @Override
  public void setConsiderCaretPositionOnDocumentUpdates(boolean enabled) {}

  @Override
  public void execute(boolean batchMode, @NotNull Runnable operation) {
    operation.run();
  }

  @Override
  public boolean isInBatchMode() {
    return false;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
  }
}
