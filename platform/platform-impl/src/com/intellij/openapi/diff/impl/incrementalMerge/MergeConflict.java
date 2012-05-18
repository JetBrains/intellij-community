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

/**
 * Represents a merge conflict, i.e. two {@link ConflictChange conflicting changes}, one from left, another from right.
 */
class MergeConflict extends ChangeType.ChangeSide implements DiffRangeMarker.RangeInvalidListener {

  @NotNull private final MergeList myMergeList;
  @NotNull private final DiffRangeMarker myCommonRange;
  @NotNull private final ConflictChange myLeftChange;
  @NotNull private final ConflictChange myRightChange;
  @NotNull private final Change.HighlighterHolder myCommonHighlighterHolder = new Change.HighlighterHolder();

  MergeConflict(TextRange commonRange, MergeList mergeList, TextRange leftMarker, TextRange rightMarker) {
    myCommonRange = new DiffRangeMarker((DocumentEx)mergeList.getBaseDocument(),commonRange, this);
    myMergeList = mergeList;
    myLeftChange = new ConflictChange(this, FragmentSide.SIDE1, leftMarker);
    myRightChange = new ConflictChange(this, FragmentSide.SIDE2, rightMarker);
  }

  public Change.HighlighterHolder getHighlighterHolder() {
    return myCommonHighlighterHolder;
  }

  public DiffRangeMarker getRange() {
    return myCommonRange;
  }

  @NotNull
  public ConflictChange getLeftChange() {
    return myLeftChange;
  }

  @NotNull
  public ConflictChange getRightChange() {
    return myRightChange;
  }

  public void conflictRemoved() {
    removeHighlighters(myLeftChange);
    removeHighlighters(myRightChange);
    myCommonHighlighterHolder.removeHighlighters();
    myMergeList.removeChanges(myLeftChange, myRightChange);
    myCommonRange.removeListener(this);
  }

  private static void removeHighlighters(@NotNull ConflictChange change) {
    change.getOriginalSide().getHighlighterHolder().removeHighlighters();
  }

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
