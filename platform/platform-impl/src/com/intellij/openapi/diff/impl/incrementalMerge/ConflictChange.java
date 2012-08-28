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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;

/**
 * One of two conflicting changes.
 * @see MergeConflict
 */
class ConflictChange extends Change implements DiffRangeMarker.RangeInvalidListener {

  private static final Logger LOG = Logger.getInstance(ConflictChange.class);

  private SimpleChangeSide myOriginalSide;
  private MergeConflict myConflict;
  private final ChangeList myChangeList;
  private ChangeType myType;
  private boolean mySemiApplied;

  public ConflictChange(MergeConflict conflict, FragmentSide mergeSide, TextRange range, ChangeList changeList) {
    myConflict = conflict;
    myChangeList = changeList;
    myOriginalSide = new SimpleChangeSide(mergeSide, new DiffRangeMarker((DocumentEx)conflict.getOriginalDocument(mergeSide), range, this));
    myType = ChangeType.CONFLICT;
  }

  @Override
  protected void changeSide(ChangeSide sideToChange, DiffRangeMarker newRange) {
    myConflict.setRange(newRange);
  }

  protected void removeFromList() {
    myConflict.conflictRemoved();
    myConflict = null;
  }

  public ChangeSide getChangeSide(FragmentSide side) {
    return isBranch(side) ? myOriginalSide : myConflict;
  }

  private static boolean isBranch(FragmentSide side) {
    return MergeList.BRANCH_SIDE == side;
  }

  public SimpleChangeSide getOriginalSide() {
    return myOriginalSide;
  }

  public ChangeType getType() {
    return myType;
  }

  public ChangeList getChangeList() {
    return myConflict.getMergeList().getChanges(myOriginalSide.getFragmentSide());
  }

  @Override
  public void onApplied() {
    myType = ChangeType.deriveApplied(myType);
    myChangeList.apply(this);

    myOriginalSide.getHighlighterHolder().updateHighlighter(myOriginalSide, myType);
    myOriginalSide.getHighlighterHolder().setActions(new AnAction[0]);

    // display, what one side of the conflict was resolved to
    myConflict.getHighlighterHolder().updateHighlighter(myConflict, myType);

    // update the other variant of the conflict to point to the bottom
    if (!mySemiApplied) {
      ConflictChange otherChange = myConflict.getOtherChange(this);
      LOG.assertTrue(otherChange != null, String.format("Other change is null. This change: %s Merge conflict: %s", this, myConflict));
      otherChange.mySemiApplied = true;
      otherChange.updateOtherSideOnConflictApply();
      myConflict.removeOtherChange(this);
    }
  }

  private void updateOtherSideOnConflictApply() {
    int startOffset = myConflict.getRange().getEndOffset();
    TextRange emptyRange = new TextRange(startOffset, startOffset);
    ConflictChange leftChange = isBranch(myOriginalSide.getFragmentSide()) ? null : this;
    ConflictChange rightChange = isBranch(myOriginalSide.getFragmentSide()) ? this : null;
    myConflict = myConflict.deriveSideForNotAppliedChange(emptyRange, leftChange, rightChange);
    myOriginalSide.getHighlighterHolder().updateHighlighter(myOriginalSide, myType);
    myConflict.getHighlighterHolder().updateHighlighter(myConflict, myType);
    myChangeList.fireOnChangeApplied();
  }

  public void onRemovedFromList() {
    myOriginalSide.getRange().removeListener(this);
    myConflict = null;
    myOriginalSide = null;
  }

  public boolean isValid() {
    return myConflict != null;
  }

  public void onRangeInvalidated() {
    removeFromList();
  }
}
