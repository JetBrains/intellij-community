// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface InlayModelEx extends InlayModel {

  int getHeightOfBlockElementsBeforeVisualLine(int visualLine, int startOffset, int prevFoldRegionIndex);

  default @Nullable Inlay<?> getWidestVisibleBlockInlay() {
    Inlay<?> widestInlay = null;
    int maxWidth = -1;
    for (Inlay<?> inlay : getBlockElementsInRange(0, Integer.MAX_VALUE)) {
      int width = inlay.getWidthInPixels();
      if (width > maxWidth && !EditorUtil.isInlayFolded(inlay)) {
        maxWidth = width;
        widestInlay = inlay;
      }
    }
    return widestInlay;
  }
}
