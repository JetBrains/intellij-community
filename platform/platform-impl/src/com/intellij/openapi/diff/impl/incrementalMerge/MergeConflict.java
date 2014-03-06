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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a merge conflict, i.e. two {@link ConflictChange conflicting changes}, one from left, another from right.
 */
class MergeConflict extends ChangeSide implements DiffRangeMarker.RangeInvalidListener {

  @NotNull private final MergeList myMergeList;
  @NotNull private DiffRangeMarker myCommonRange;
  @Nullable private ConflictChange myLeftChange;
  @Nullable private ConflictChange myRightChange;
  @NotNull private final ChangeHighlighterHolder myCommonHighlighterHolder;

  MergeConflict(@NotNull TextRange commonRange,
                @NotNull MergeList mergeList,
                @NotNull TextRange leftMarker,
                @NotNull TextRange rightMarker) {
    myCommonRange = new DiffRangeMarker((DocumentEx)mergeList.getBaseDocument(), commonRange, this);
    myMergeList = mergeList;
    myCommonHighlighterHolder = new ChangeHighlighterHolder();
    myLeftChange = new ConflictChange(this, FragmentSide.SIDE1, leftMarker, mergeList.getLeftChangeList());
    myRightChange = new ConflictChange(this, FragmentSide.SIDE2, rightMarker, mergeList.getRightChangeList());
  }

  private MergeConflict(@NotNull TextRange commonRange, @NotNull MergeList mergeList, @NotNull ChangeHighlighterHolder highlighterHolder,
                        @Nullable ConflictChange leftChange, @Nullable ConflictChange rightChange) {
    myCommonRange = new DiffRangeMarker((DocumentEx)mergeList.getBaseDocument(), commonRange, this);
    myMergeList = mergeList;
    myCommonHighlighterHolder = highlighterHolder;
    myLeftChange = leftChange;
    myRightChange = rightChange;
  }

  @NotNull
  public MergeConflict deriveSideForNotAppliedChange(@NotNull TextRange baseRange,
                                                     @Nullable ConflictChange leftChange,
                                                     @Nullable ConflictChange rightChange) {
    ChangeHighlighterHolder highlighterHolder = new ChangeHighlighterHolder();
    MergeConflict mergeConflict = new MergeConflict(baseRange, myMergeList, highlighterHolder, leftChange, rightChange);
    highlighterHolder.highlight(mergeConflict, myCommonHighlighterHolder.getEditor(), ChangeType.CONFLICT);
    return mergeConflict;
  }

  @NotNull
  public ChangeHighlighterHolder getHighlighterHolder() {
    return myCommonHighlighterHolder;
  }

  @NotNull
  public DiffRangeMarker getRange() {
    return myCommonRange;
  }

  @Nullable
  public ConflictChange getLeftChange() {
    return myLeftChange;
  }

  @Nullable
  public ConflictChange getRightChange() {
    return myRightChange;
  }

  @Nullable
  ConflictChange getOtherChange(@NotNull ConflictChange change) {
    if (change == myLeftChange) {
      return myRightChange;
    }
    else if (change == myRightChange) {
      return myLeftChange;
    }
    else {
      throw new IllegalStateException("Unexpected change: " + change);
    }
  }

  public void conflictRemoved() {
    removeHighlighters(myLeftChange);
    removeHighlighters(myRightChange);
    myCommonHighlighterHolder.removeHighlighters();
    myMergeList.removeChanges(myLeftChange, myRightChange);
    myCommonRange.removeListener(this);
  }

  public void setRange(@NotNull DiffRangeMarker range) {
    myCommonRange = range;
  }

  public void removeOtherChange(@NotNull ConflictChange change) {
    if (change == myLeftChange) {
      myRightChange = null;
    }
    else if (change == myRightChange) {
      myLeftChange = null;
    }
    else {
      throw new IllegalStateException("Unexpected change: " + change);
    }
  }

  private static void removeHighlighters(@Nullable ConflictChange change) {
    if (change != null) {
      change.getOriginalSide().getHighlighterHolder().removeHighlighters();
    }
  }

  @NotNull
  public Document getOriginalDocument(FragmentSide mergeSide) {
    return myMergeList.getChanges(mergeSide).getDocument(MergeList.BRANCH_SIDE);
  }

  public void onRangeInvalidated() {
    conflictRemoved();
  }

  @NotNull
  public MergeList getMergeList() {
    return myMergeList;
  }

}
