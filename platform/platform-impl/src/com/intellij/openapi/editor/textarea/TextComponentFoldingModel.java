// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TextComponentFoldingModel implements FoldingModel {

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return null;
  }

  @Override
  public void removeFoldRegion(@NotNull FoldRegion region) {
  }

  @Override
  public FoldRegion @NotNull [] getAllFoldRegions() {
    return FoldRegion.EMPTY_ARRAY;
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    return false;
  }
  
  @Override
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return null;
  }

  @Override
  public @Nullable FoldRegion getFoldRegion(int startOffset, int endOffset) {
    return null;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean allowMovingCaret, boolean keepRelativeCaretPosition) {
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaretFromCollapsedRegion) {
  }
}
