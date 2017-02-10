/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

public class FoldingTransformation implements Transformation {
  private final Editor myEditor;
  private final ArrayList<FoldRegion> myCollapsed = new ArrayList<>();
  private final int[] myFoldBeginings;

  public FoldingTransformation(Editor editor) {
    myEditor = editor;
    FoldRegion[] foldRegions = myEditor.getFoldingModel().getAllFoldRegions();
    Arrays.sort(foldRegions, RangeMarker.BY_START_OFFSET);
    TIntArrayList foldBeginings = new TIntArrayList();
    for (FoldRegion foldRegion : foldRegions) {
      if (!foldRegion.isValid() || foldRegion.isExpanded()) continue;
      foldBeginings.add(getStartLine(foldRegion));
      myCollapsed.add(foldRegion);
    }
    myFoldBeginings = foldBeginings.toNativeArray();
  }

  private int getStartLine(FoldRegion foldRegion) {
    return myEditor.offsetToLogicalPosition(foldRegion.getStartOffset()).line;
//    return ((FoldRegionImpl)foldRegion).getStartLine();
  }

  public int transform(int line) {
    FoldRegion foldRegion = findFoldRegion(line);
    int yOffset = 0;
    if (foldRegion != null) {
      int startLine = getStartLine(foldRegion);
      yOffset = (int)((double)(line - startLine) / getLineLength(foldRegion) * myEditor.getLineHeight());
      line = startLine;
    }
    yOffset += myEditor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

    final JComponent header = myEditor.getHeaderComponent();
    int headerOffset = header == null ? 0 : header.getHeight();

    return yOffset - myEditor.getScrollingModel().getVerticalScrollOffset() + headerOffset;
  }

  private int getLineLength(FoldRegion foldRegion) {
    return getEndLine(foldRegion) - getStartLine(foldRegion);
  }

  private int getEndLine(FoldRegion foldRegion) {
    return myEditor.offsetToLogicalPosition(foldRegion.getEndOffset()).line;
//    return ((FoldRegionImpl)foldRegion).getEndLine();
  }

  private FoldRegion findFoldRegion(int line) {
    int index = Arrays.binarySearch(myFoldBeginings, line);
    FoldRegion region;
    if (index >= 0) region = myCollapsed.get(index);
    else {
      index = -index - 1;
      if (index == 0) return null;
      region = myCollapsed.get(index - 1);
    }
    if (getEndLine(region) < line) return null;
    return region;
  }
}
