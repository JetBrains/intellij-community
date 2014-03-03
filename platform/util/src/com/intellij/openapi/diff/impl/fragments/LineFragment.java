/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;

public class LineFragment extends LineBlock implements Fragment {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.fragments.LineFragment");
  private final TextRange myRange1;
  private final TextRange myRange2;
  private FragmentList myChildren;
  private boolean myHasLineChildren;

  public LineFragment(int startingLine1, int modifiedLines1,
                      int startingLine2, int modifiedLines2,
                      TextDiffTypeEnum blockType, TextRange range1, TextRange range2) {
    this(startingLine1, modifiedLines1,
         startingLine2, modifiedLines2,
         blockType, range1, range2, FragmentList.EMPTY);
  }

  private LineFragment(int startingLine1, int modifiedLines1,
                       int startingLine2, int modifiedLines2,
                       TextDiffTypeEnum blockType, TextRange range1, TextRange range2, FragmentList children) {
    super(startingLine1, modifiedLines1, startingLine2, modifiedLines2, blockType);
    LOG.assertTrue(modifiedLines1 > 0 || modifiedLines2 > 0);
    myRange1 = range1;
    myRange2 = range2;
    myChildren = children;
    checkChildren(myChildren.iterator());
  }


  @Override
  public TextRange getRange(FragmentSide side) {
    if (side == FragmentSide.SIDE1) return myRange1;
    if (side == FragmentSide.SIDE2) return myRange2;
    throw new IllegalArgumentException(String.valueOf(side));
  }

  @Override
  public Fragment shift(TextRange range1, TextRange range2, int startingLine1, int startingLine2) {
    return new LineFragment(startingLine1 + getStartingLine1(), getModifiedLines1(),
                            startingLine2 + getStartingLine2(), getModifiedLines2(),
                            getType(), shiftRange(range1, myRange1), shiftRange(range2, myRange2),
                            myChildren.shift(range1, range2, startingLine1, startingLine2));
  }

  /**
   * <p>Adjusts the diff type of this line fragment based on the types of the inline child fragments.</p>
   * <p>For example, a modification in terms of line fragment may be just one inline insertion.
   * In this case it is better to think about the whole change as of insertion.</p>
   */
  public void adjustTypeFromChildrenTypes() {
    if (getType() != TextDiffTypeEnum.CHANGED) { // if the change is already insertion or deletion, no need to adjust
      return;
    }

    TextDiffTypeEnum candidateType = null;
    for (Iterator<Fragment> children = getChildrenIterator(); children != null && children.hasNext(); ) {
      TextDiffTypeEnum fragmentType = children.next().getType();
      if (fragmentType == null) {
        continue;
      }
      switch (fragmentType) {
        case CHANGED: // inline change => everything is a change
          return;
        case INSERT:
          if (candidateType == null) {
            candidateType = TextDiffTypeEnum.INSERT;
          }
          else if (candidateType != TextDiffTypeEnum.INSERT) {
            return; // different changes (insertion and deletion) inside a single line => everything is a change
          }
          break;
        case DELETED:
          if (candidateType == null) {
            candidateType = TextDiffTypeEnum.DELETED;
          }
          else if (candidateType != TextDiffTypeEnum.DELETED) {
            return;
          }
          break;
        default:
          // should not happen, because conflicts can happen only in merge tools, where there are no inline changes for now,
          // but we don't want to modify the fragment in this doubtful case anyway.
          return;
      }
    }

    if (candidateType != null) {
      setType(candidateType);
    }
  }

  static TextRange shiftRange(TextRange shift, TextRange range) {
    int start = shift.getStartOffset();
    int newEnd = start + range.getEndOffset();
    int newStart = start + range.getStartOffset();
    LOG.assertTrue(newStart <= shift.getEndOffset());
    LOG.assertTrue(newEnd <= shift.getEndOffset());
    return new TextRange(newStart, newEnd);
  }

  @Override
  public void highlight(FragmentHighlighter fragmentHighlighter) {
    fragmentHighlighter.highlightLine(this);
  }

  public boolean isOneSide() {
    return myRange1.getLength() == 0 || myRange2.getLength() == 0;
  }

  public boolean isEqual() {
    return getType() == null;
  }

  @Override
  public Fragment getSubfragmentAt(int offset, FragmentSide side, Condition<Fragment> condition) {
    Fragment childFragment = myChildren.getFragmentAt(offset, side, condition);
    return childFragment != null ? childFragment : this;
  }

  @Nullable
  public Iterator<Fragment> getChildrenIterator() {
    return myChildren == null || myChildren.isEmpty() ? null : myChildren.iterator();
  }

  @NotNull
  public DiffString getText(@NotNull DiffString text, @NotNull FragmentSide side) {
    TextRange range = getRange(side);
    return text.substring(range.getStartOffset(), range.getEndOffset());
  }


  public void addAllDescendantsTo(ArrayList<LineFragment> descendants) {
    if (myChildren == null) return;
    for (Iterator<Fragment> iterator = myChildren.iterator(); iterator.hasNext();) {
      Fragment fragment = iterator.next();
      if (fragment instanceof LineFragment) {
        LineFragment lineFragment = (LineFragment)fragment;
        descendants.add(lineFragment);
        lineFragment.addAllDescendantsTo(descendants);
      }
    }
  }

  public void setChildren(ArrayList<Fragment> fragments) {
    LOG.assertTrue(myChildren == FragmentList.EMPTY);
    ArrayList<Fragment> shifted =
        FragmentListImpl.shift(fragments, myRange1, myRange2, getStartingLine1(), getStartingLine2());
    if (shifted.isEmpty()) return;
    Fragment firstChild = shifted.get(0);
    if (shifted.size() == 1 && isSameRanges(firstChild)) {
      if (!(firstChild instanceof LineFragment)) return;
      LineFragment lineFragment = (LineFragment)firstChild;
      myChildren = lineFragment.myChildren;
    } else myChildren = FragmentListImpl.fromList(shifted);
    checkChildren(myChildren.iterator());
  }

  private void checkChildren(Iterator<Fragment> iterator) {
    if (myChildren.isEmpty()) {
      myHasLineChildren = false;
      return;
    }
    boolean hasLineChildren = false;
    boolean hasInlineChildren = false;
    while(iterator.hasNext()) {
      Fragment fragment = iterator.next();
      boolean lineChild = fragment instanceof LineFragment;
      hasLineChildren |= lineChild;
      hasInlineChildren |= !lineChild;
      if (lineChild) {
        LineFragment lineFragment = (LineFragment)fragment;
        LOG.assertTrue(getStartingLine1() != lineFragment.getStartingLine1() ||
                       getModifiedLines1() != lineFragment.getModifiedLines1() ||
                       getStartingLine2() != lineFragment.getStartingLine2() ||
                       getModifiedLines2() != lineFragment.getModifiedLines2());
      }
    }
    LOG.assertTrue(hasLineChildren ^ hasInlineChildren);
    myHasLineChildren = hasLineChildren;
  }

  private boolean isSameRanges(Fragment fragment) {
    return getRange(FragmentSide.SIDE1).equals(fragment.getRange(FragmentSide.SIDE1)) &&
           getRange(FragmentSide.SIDE2).equals(fragment.getRange(FragmentSide.SIDE2));
  }

  public boolean isHasLineChildren() {
    return myHasLineChildren;
  }

  @Override
  public int getEndLine1() {
    return super.getEndLine1();
  }

  @Override
  public int getEndLine2() {
    return super.getEndLine2();
  }
}
