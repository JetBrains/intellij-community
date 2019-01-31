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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
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
        if (!isFoldingPossible(startOffset, endOffset)) continue;

        int foldStart = myEditor.offsetToVisualLine(startOffset);
        if (!region.isExpanded()) {
          tryAdding(result, region, foldStart, 0, DisplayedFoldingAnchor.Type.COLLAPSED, activeFoldRegions);
        }
        else {
          int foldEnd = myEditor.offsetToVisualLine(endOffset);
          tryAdding(result, region, foldStart, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_TOP, activeFoldRegions);
          tryAdding(result, region, foldEnd, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_BOTTOM, activeFoldRegions);
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
    if (prev != null) {
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

  /**
   * Allows to answer if there may be folding for the given offsets.
   * <p/>
   * The rule is that we can fold range that occupies multiple logical or visual lines.
   *
   * @param startOffset   start offset of the target region to check
   * @param endOffset     end offset of the target region to check
   */
  private boolean isFoldingPossible(int startOffset, int endOffset) {
    Document document = myEditor.getDocument();
    if (startOffset >= document.getTextLength()) {
      return false;
    }

    int endOffsetToUse = Math.min(endOffset, document.getTextLength());
    if (endOffsetToUse <= startOffset) {
      return false;
    }

    if (document.getLineNumber(startOffset) != document.getLineNumber(endOffsetToUse)) {
      return true;
    }
    return myEditor.getSettings().isAllowSingleLogicalLineFolding()
           && !myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffsetToUse).isEmpty();
  }
}
