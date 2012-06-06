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
import org.jetbrains.annotations.NotNull;

class SimpleChange extends Change implements DiffRangeMarker.RangeInvalidListener{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.Change");
  private ChangeType myType;
  private final SimpleChangeSide[] mySides;
  private final ChangeList myChangeList;

  public SimpleChange(ChangeType type, @NotNull TextRange range1, @NotNull TextRange range2, ChangeList changeList) {
    mySides = new SimpleChangeSide[]{createSide(changeList, range1, FragmentSide.SIDE1),
                         createSide(changeList, range2, FragmentSide.SIDE2)};
    myType = type;
    myChangeList = changeList;
  }

  private SimpleChangeSide createSide(ChangeList changeList, TextRange range1, FragmentSide side) {
    return new SimpleChangeSide(side, new DiffRangeMarker((DocumentEx)changeList.getDocument(side), range1, this));
  }

  /**
   * Changes the given Side of a Change to a new text range.
   * @param sideToChange Side to be changed.
   * @param newRange     New change range.
   */
  @Override
  protected void changeSide(ChangeSide sideToChange, DiffRangeMarker newRange) {
    for (int i = 0; i < mySides.length; i++) {
      SimpleChangeSide side = mySides[i];
      if (side.equals(sideToChange)) {
        mySides[i] = new SimpleChangeSide(sideToChange, newRange);
        break;
      }
    }
  }

  protected void removeFromList() {
    myChangeList.remove(this);
  }

  public ChangeSide getChangeSide(FragmentSide side) {
    return mySides[side.getIndex()];
  }

  public ChangeType getType() {
    return myType;
  }

  public ChangeList getChangeList() {
    return myChangeList;
  }

  @Override
  public void onApplied() {
    myType = ChangeType.deriveApplied(myType);
    for (SimpleChangeSide side : mySides) {
      ChangeHighlighterHolder highlighterHolder = side.getHighlighterHolder();
      highlighterHolder.setActions(new AnAction[0]);
      highlighterHolder.updateHighlighter(side, myType);
    }
    myChangeList.apply(this);
  }

  public void onRemovedFromList() {
    for (int i = 0; i < mySides.length; i++) {
      SimpleChangeSide side = mySides[i];
      side.getRange().removeListener(this);
      side.getHighlighterHolder().removeHighlighters();
      mySides[i] = null;
    }
  }

  public boolean isValid() {
    LOG.assertTrue((mySides[0] == null) == (mySides[1] == null));
    return mySides[0] != null;
  }

  public void onRangeInvalidated() {
    myChangeList.remove(this);
  }

  public static Change fromRanges(@NotNull TextRange baseRange, @NotNull TextRange versionRange, ChangeList changeList) {
    ChangeType type = ChangeType.fromRanges(baseRange, versionRange);
    return new SimpleChange(type, baseRange, versionRange, changeList);
  }
}
