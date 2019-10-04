/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FoldingAnchorsOverlayStrategy {
  private final EditorImpl myEditor;

  FoldingAnchorsOverlayStrategy(EditorImpl editor) {
    myEditor = editor;
  }

  @NotNull
  Collection<DisplayedFoldingAnchor> getAnchorsToDisplay(int firstVisibleOffset,
                                                         int lastVisibleOffset,
                                                         @NotNull List<FoldRegion> activeFoldRegions) {
    Map<Integer, DisplayedFoldingAnchor> result = new HashMap<>();
    FoldRegion[] visibleFoldRegions = myEditor.getFoldingModel().fetchVisible();
    if (visibleFoldRegions != null) {
      for (FoldRegion region : visibleFoldRegions) {
        if (!region.isValid()) continue;
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

  private static void tryAdding(@NotNull Map<Integer, DisplayedFoldingAnchor> resultsMap,
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
}
