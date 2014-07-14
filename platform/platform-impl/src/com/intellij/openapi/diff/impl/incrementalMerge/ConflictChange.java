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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * One of two conflicting changes.
 *
 * @see MergeConflict
 */
class ConflictChange extends TwoSideChange.SideChange<MergeConflict> {

  private static final Logger LOG = Logger.getInstance(ConflictChange.class);

  private boolean mySemiApplied;

  public ConflictChange(@NotNull MergeConflict conflict,
                        @NotNull FragmentSide mergeSide,
                        @NotNull TextRange versionRange,
                        @NotNull ChangeList changeList) {
    super(conflict, changeList, ChangeType.CONFLICT, mergeSide, versionRange);
  }

  @Override
  public void onApplied() {
    markApplied();

    // update the other variant of the conflict to point to the bottom
    if (!mySemiApplied) {
      ConflictChange otherChange = myTwoSideChange.getOtherChange(this);
      LOG.assertTrue(otherChange != null, String.format("Other change is null. This change: %s Merge conflict: %s", this, myTwoSideChange));
      otherChange.mySemiApplied = true;
      otherChange.updateOtherSideOnConflictApply();
      myTwoSideChange.removeOtherChange(this);
    }
  }

  private void updateOtherSideOnConflictApply() {
    if (myOriginalSide.getStart() == myOriginalSide.getEnd()) {
      markApplied();
      return;
    }

    int startOffset = myTwoSideChange.getRange().getEndOffset();
    TextRange emptyRange = new TextRange(startOffset, startOffset);
    ConflictChange leftChange = isBranch(myOriginalSide.getFragmentSide()) ? null : this;
    ConflictChange rightChange = isBranch(myOriginalSide.getFragmentSide()) ? this : null;
    myTwoSideChange = myTwoSideChange.deriveSideForNotAppliedChange(emptyRange, leftChange, rightChange);
    myOriginalSide.getHighlighterHolder().updateHighlighter(myOriginalSide, myType);
    myTwoSideChange.getHighlighterHolder().updateHighlighter(myTwoSideChange, myType);
    myChangeList.fireOnChangeApplied();
  }
}
