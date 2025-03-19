// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class EmptyInlayModel implements InlayModel {
  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
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

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                                     @NotNull InlayProperties properties,
                                                                                     @NotNull T renderer) {
    return null;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                                    boolean relatesToPrecedingText,
                                                                                    boolean showAbove,
                                                                                    int priority,
                                                                                    @NotNull T renderer) {
    return null;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                                    @NotNull InlayProperties properties,
                                                                                    @NotNull T renderer) {
    return null;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addAfterLineEndElement(int offset,
                                                                                           boolean relatesToPrecedingText,
                                                                                           @NotNull T renderer) {
    return null;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addAfterLineEndElement(int offset,
                                                                                           @NotNull InlayProperties properties,
                                                                                           @NotNull T renderer) {
    return null;
  }

  @Override
  public @NotNull List<Inlay<?>> getInlineElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<Inlay<?>> getBlockElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<Inlay<?>> getBlockElementsForVisualLine(int visualLine, boolean above) {
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    return false;
  }

  @Override
  public @Nullable Inlay<?> getInlineElementAt(@NotNull VisualPosition visualPosition) {
    return null;
  }

  @Override
  public @Nullable Inlay<?> getElementAt(@NotNull Point point) {
    return null;
  }

  @Override
  public @NotNull List<Inlay<?>> getAfterLineEndElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<Inlay<?>> getAfterLineEndElementsForLogicalLine(int logicalLine) {
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
