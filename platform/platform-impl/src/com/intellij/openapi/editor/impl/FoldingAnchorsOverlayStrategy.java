// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class FoldingAnchorsOverlayStrategy {
  private final EditorImpl myEditor;

  @VisibleForTesting
  public FoldingAnchorsOverlayStrategy(EditorImpl editor) {
    myEditor = editor;
  }

  @NotNull
  @VisibleForTesting
  public Collection<DisplayedFoldingAnchor> getAnchorsToDisplay(int firstVisibleOffset,
                                                         int lastVisibleOffset,
                                                         @NotNull List<FoldRegion> activeFoldRegions) {
    Int2ObjectMap<DisplayedFoldingAnchor> result = new Int2ObjectOpenHashMap<>();
    FoldRegion[] visibleFoldRegions = myEditor.getFoldingModel().fetchVisible();
    if (visibleFoldRegions != null) {
      for (FoldRegion region : visibleFoldRegions) {
        if (!region.isValid() || region.shouldNeverExpand()) continue;
        final int startOffset = region.getStartOffset();
        if (startOffset > lastVisibleOffset) continue;
        final int endOffset = region.getEndOffset();
        if (endOffset < firstVisibleOffset) continue;

        boolean singleLine = false;
        int startLogicalLine = myEditor.getDocument().getLineNumber(startOffset);
        int endLogicalLine = myEditor.getDocument().getLineNumber(endOffset);
        if (startLogicalLine == endLogicalLine) {
          singleLine = true;
          if (!region.isGutterMarkEnabledForSingleLine() &&
              (!myEditor.getSettings().isAllowSingleLogicalLineFolding() || (endOffset - startOffset) <= 1 ||
                myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset + 1, endOffset - 1).isEmpty())) {
            // unless requested, we don't display markers for single-line fold regions
            continue;
          }
        }

        if (skipFoldingAnchor(region)) {
          continue;
        }

        int foldStart = myEditor.offsetToVisualLine(startOffset);
        if (!region.isExpanded()) {
          tryAdding(result, region, foldStart, 0,
                    singleLine ? DisplayedFoldingAnchor.Type.COLLAPSED_SINGLE_LINE : DisplayedFoldingAnchor.Type.COLLAPSED,
                    activeFoldRegions);
        }
        else {
          int foldEnd = myEditor.offsetToVisualLine(endOffset);
          if (foldStart == foldEnd) {
            tryAdding(result, region, foldStart, 0, DisplayedFoldingAnchor.Type.EXPANDED_SINGLE_LINE, activeFoldRegions);
          }
          else {
            tryAdding(result, region, foldStart, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_TOP, activeFoldRegions);
            tryAdding(result, region, foldEnd, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_BOTTOM, activeFoldRegions);
          }
        }
      }
    }
    return result.values();
  }

  private static void tryAdding(@NotNull Int2ObjectMap<DisplayedFoldingAnchor> resultsMap,
                                @NotNull FoldRegion region,
                                int visualLine,
                                int visualHeight,
                                @NotNull DisplayedFoldingAnchor.Type type,
                                @NotNull List<FoldRegion> activeRegions) {
    DisplayedFoldingAnchor prev = resultsMap.get(visualLine);
    if (prev != null && !prev.type.singleLine) {
      if (type.singleLine) {
        // show single-line marks only if there are no other marks on the same line
        return;
      }
      if (region.getGroup() != null && region.getGroup() == prev.foldRegion.getGroup() &&
          type != DisplayedFoldingAnchor.Type.COLLAPSED && type != prev.type) {
        // when top/bottom marks for regions from the same group overlap, don't show them at all
        resultsMap.remove(visualLine);
        return;
      }
      if (activeRegions.contains(prev.foldRegion)) {
        return;
      }
      if (!activeRegions.contains(region) && prev.foldRegionVisualLines < visualHeight) {
        return;
      }
    }
    resultsMap.put(visualLine, new DisplayedFoldingAnchor(region, visualLine, visualHeight, type));
  }

  private static boolean skipFoldingAnchor(@NotNull FoldRegion region) {
    if (!region.isExpanded()) {
      return Boolean.TRUE.equals(region.getUserData(FoldingKeys.HIDE_GUTTER_RENDERER_FOR_COLLAPSED));
    }
    return false;
  }
}
