package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class FoldingTransformation implements Transformation {
  private final Editor myEditor;
  private final ArrayList<FoldRegion> myCollapsed = new ArrayList<FoldRegion>();
  private final int[] myFoldBeginings;
  private static final Comparator<FoldRegion> FOLD_REGIONS_COMPARATOR = new Comparator<FoldRegion>(){
      public int compare(FoldRegion foldRegion, FoldRegion foldRegion1) {
        return foldRegion.getStartOffset() - foldRegion1.getStartOffset();
      }
    };

  public FoldingTransformation(Editor editor) {
    myEditor = editor;
    FoldRegion[] foldRegions = myEditor.getFoldingModel().getAllFoldRegions();
    Arrays.sort(foldRegions, FOLD_REGIONS_COMPARATOR);
    TIntArrayList foldBeginings = new TIntArrayList();
    for (int i = 0; i < foldRegions.length; i++) {
      FoldRegion foldRegion = foldRegions[i];
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
