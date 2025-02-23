// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DisplayedFoldingAnchor {
  public enum Type {
    COLLAPSED(false),
    COLLAPSED_SINGLE_LINE(true),
    EXPANDED_TOP(false),
    EXPANDED_BOTTOM(false),
    EXPANDED_SINGLE_LINE(true);

    public final boolean singleLine;

    Type(boolean singleLine) {this.singleLine = singleLine;}
  }

  final @NotNull FoldRegion foldRegion;
  public final int visualLine;
  final int foldRegionVisualLines;
  public final @NotNull Type type;

  DisplayedFoldingAnchor(@NotNull FoldRegion foldRegion, int visualLine, int foldRegionVisualLines, @NotNull Type type) {
    this.foldRegion = foldRegion;
    this.visualLine = visualLine;
    this.foldRegionVisualLines = foldRegionVisualLines;
    this.type = type;
  }
}
