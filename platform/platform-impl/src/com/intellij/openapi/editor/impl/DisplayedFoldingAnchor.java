// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

final class DisplayedFoldingAnchor {
  enum Type {
    COLLAPSED(false),
    COLLAPSED_SINGLE_LINE(true),
    EXPANDED_TOP(false),
    EXPANDED_BOTTOM(false),
    EXPANDED_SINGLE_LINE(true);

    public final boolean singleLine;

    Type(boolean singleLine) {this.singleLine = singleLine;}
  }

  final @NotNull FoldRegion foldRegion;
  final int visualLine;
  final int foldRegionVisualLines;
  final @NotNull Type type;

  DisplayedFoldingAnchor(@NotNull FoldRegion foldRegion, int visualLine, int foldRegionVisualLines, @NotNull Type type) {
    this.foldRegion = foldRegion;
    this.visualLine = visualLine;
    this.foldRegionVisualLines = foldRegionVisualLines;
    this.type = type;
  }
}
