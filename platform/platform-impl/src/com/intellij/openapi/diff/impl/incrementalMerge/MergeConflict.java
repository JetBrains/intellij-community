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
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a merge conflict, i.e. two {@link ConflictChange conflicting changes}, one from left, another from right.
 */
class MergeConflict extends TwoSideChange<ConflictChange> {
  MergeConflict(@NotNull TextRange baseRange,
                @NotNull TextRange leftRange,
                @NotNull TextRange rightRange,
                @NotNull MergeList mergeList) {
    super(baseRange, mergeList, new ChangeHighlighterHolder());
    myLeftChange = new ConflictChange(this, FragmentSide.SIDE1, leftRange, mergeList.getLeftChangeList());
    myRightChange = new ConflictChange(this, FragmentSide.SIDE2, rightRange, mergeList.getRightChangeList());
  }

  private MergeConflict(@NotNull TextRange baseRange,
                        @Nullable ConflictChange leftChange,
                        @Nullable ConflictChange rightChange,
                        @NotNull MergeList mergeList,
                        @NotNull ChangeHighlighterHolder highlighterHolder) {
    super(baseRange, mergeList, highlighterHolder);
    myLeftChange = leftChange;
    myRightChange = rightChange;
  }

  @NotNull
  public MergeConflict deriveSideForNotAppliedChange(@NotNull TextRange baseRange,
                                                     @Nullable ConflictChange leftChange,
                                                     @Nullable ConflictChange rightChange) {
    ChangeHighlighterHolder highlighterHolder = new ChangeHighlighterHolder();
    MergeConflict mergeConflict = new MergeConflict(baseRange, leftChange, rightChange, myMergeList, highlighterHolder);
    highlighterHolder.highlight(mergeConflict, myCommonHighlighterHolder.getEditor(), ChangeType.CONFLICT);
    return mergeConflict;
  }
}
