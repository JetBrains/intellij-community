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
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.util.containers.hash.HashMap;

import java.util.Collection;
import java.util.Map;

class FoldingAnchorsOverlayStrategy {
  private final EditorImpl myEditor;

  public FoldingAnchorsOverlayStrategy(EditorImpl editor) {
    myEditor = editor;
  }

  public Collection<DisplayedFoldingAnchor> getAnchorsToDisplay(int firstVisibleOffset, int lastVisibleOffset, FoldRegion activeFoldRegion) {
    Map<Integer, DisplayedFoldingAnchor> result = new HashMap<>();
    FoldRegion[] visibleFoldRegions = myEditor.getFoldingModel().fetchVisible();
    for (FoldRegion region : visibleFoldRegions) {
      if (!region.isValid()) continue;
      final int startOffset = region.getStartOffset();
      if (startOffset > lastVisibleOffset) continue;
      final int endOffset = getEndOffset(region);
      if (endOffset < firstVisibleOffset) continue;
      if (!isFoldingPossible(startOffset, endOffset)) continue;

      final FoldingGroup group = region.getGroup();
      if (group != null && myEditor.getFoldingModel().getFirstRegion(group, region) != region) continue;

      //offset = Math.min(myEditor.getDocument().getTextLength() - 1, offset);
      int foldStart = myEditor.offsetToVisualLine(startOffset);

      if (!region.isExpanded()) {
        tryAdding(result, region, foldStart, 0, DisplayedFoldingAnchor.Type.COLLAPSED, activeFoldRegion);
      }
      else {
        //offset = Math.min(myEditor.getDocument().getTextLength() - 1, offset);
        int foldEnd = myEditor.offsetToVisualLine(endOffset);
        tryAdding(result, region, foldStart, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_TOP, activeFoldRegion);
        tryAdding(result, region, foldEnd, foldEnd - foldStart, DisplayedFoldingAnchor.Type.EXPANDED_BOTTOM, activeFoldRegion);
      }
    }
    return result.values();
  }

  private static void tryAdding(Map<Integer, DisplayedFoldingAnchor> resultsMap,
                         FoldRegion region,
                         int visualLine,
                         int visualHeight,
                         DisplayedFoldingAnchor.Type type,
                         FoldRegion activeRegion) {
    DisplayedFoldingAnchor prev = resultsMap.get(visualLine);
    if (prev != null) {
      if (prev.foldRegion == activeRegion) {
        return;
      }
      if (region != activeRegion && prev.foldRegionVisualLines < visualHeight) {
        return;
      }
    }
    resultsMap.put(visualLine, new DisplayedFoldingAnchor(region, visualLine, visualHeight, type));
  }

  private int getEndOffset(FoldRegion foldRange) {
    FoldingGroup group = foldRange.getGroup();
    return group == null ? foldRange.getEndOffset() : myEditor.getFoldingModel().getEndOffset(group);
  }

  /**
   * Allows to answer if there may be folding for the given offsets.
   * <p/>
   * The rule is that we can fold range that occupies multiple logical or visual lines.
   *
   * @param startOffset   start offset of the target region to check
   * @param endOffset     end offset of the target region to check
   * @return
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
