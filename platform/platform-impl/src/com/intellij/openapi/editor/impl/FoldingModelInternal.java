// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.util.IntPair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface FoldingModelInternal extends FoldingModelEx {
  int getFoldedLinesCountBefore(int offset);
  int getTotalNumberOfFoldedLines();
  void updateCachedOffsets();
  boolean isInBatchFoldingOperation();
  @NotNull IntPair getCustomRegionsYAdjustment(int offset, int prevFoldRegionIndex);
}
