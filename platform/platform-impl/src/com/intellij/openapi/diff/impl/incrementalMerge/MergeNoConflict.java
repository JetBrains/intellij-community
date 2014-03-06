package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class MergeNoConflict extends TwoSideChange<NoConflictChange> {
  MergeNoConflict(@NotNull TextRange baseRange,
                  @NotNull TextRange leftRange,
                  @NotNull TextRange rightRange,
                  @NotNull MergeList mergeList) {
    super(baseRange, mergeList, new ChangeHighlighterHolder());
    myLeftChange = new NoConflictChange(this, FragmentSide.SIDE1, baseRange, leftRange, mergeList.getLeftChangeList());
    myRightChange = new NoConflictChange(this, FragmentSide.SIDE2, baseRange, rightRange, mergeList.getRightChangeList());
  }
}
