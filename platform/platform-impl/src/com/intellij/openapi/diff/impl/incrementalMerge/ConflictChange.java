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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;

class ConflictChange extends Change implements DiffRangeMarker.RangeInvalidListener {
  private Side myOriginalSide;
  private MergeConflict myConflict;

  public ConflictChange(MergeConflict conflict, FragmentSide mergeSide, TextRange range) {
    myConflict = conflict;
    myOriginalSide = new Side(mergeSide, new DiffRangeMarker((DocumentEx)conflict.getOriginalDocument(mergeSide), range, this));
  }

  protected void removeFromList() {
    myConflict.conflictRemoved();
    myConflict = null;
  }

  public ChangeType.ChangeSide getChangeSide(FragmentSide side) {
    return isBranch(side) ? myOriginalSide : myConflict;
  }

  private boolean isBranch(FragmentSide side) {
    return MergeList.BRANCH_SIDE == side;
  }

  public Change.Side getOriginalSide() {
    return myOriginalSide;
  }

  public ChangeType getType() { return ChangeType.CONFLICT; }

  public ChangeList getChangeList() {
    return myConflict.getMergeList().getChanges(myOriginalSide.getFragmentSide());
  }

  public void onRemovedFromList() {
    myConflict.onChangeRemoved(myOriginalSide.getFragmentSide(), this);
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
