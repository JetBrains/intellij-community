/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class DiffTree<OT, NT> {
  private static final int CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD = 20;

  private final FlyweightCapableTreeStructure<OT> myOldTree;
  private final FlyweightCapableTreeStructure<NT> myNewTree;
  private final ShallowNodeComparator<OT, NT> myComparator;
  private final List<Ref<OT[]>> myOldChildrenLists = new ArrayList<Ref<OT[]>>();
  private final List<Ref<NT[]>> myNewChildrenLists = new ArrayList<Ref<NT[]>>();
  private final CharSequence myOldText;
  private final CharSequence myNewText;
  private final int myOldTreeStart;
  private final int myNewTreeStart;

  private DiffTree(@NotNull FlyweightCapableTreeStructure<OT> oldTree,
                   @NotNull FlyweightCapableTreeStructure<NT> newTree,
                   @NotNull ShallowNodeComparator<OT, NT> comparator,
                   @NotNull CharSequence oldText) {
    myOldTree = oldTree;
    myNewTree = newTree;
    myComparator = comparator;
    myOldText = oldText;
    myOldTreeStart = oldTree.getStartOffset(oldTree.getRoot());
    myNewText = newTree.toString(newTree.getRoot());
    myNewTreeStart = newTree.getStartOffset(newTree.getRoot());
  }

  public static <OT, NT> void diff(@NotNull FlyweightCapableTreeStructure<OT> oldTree,
                                   @NotNull FlyweightCapableTreeStructure<NT> newTree,
                                   @NotNull ShallowNodeComparator<OT, NT> comparator,
                                   @NotNull DiffTreeChangeBuilder<OT, NT> consumer,
                                   @NotNull CharSequence oldText) {
    final DiffTree<OT, NT> tree = new DiffTree<OT, NT>(oldTree, newTree, comparator, oldText);
    tree.build(oldTree.getRoot(), newTree.getRoot(), 0, consumer);
  }

  private enum CompareResult {
    EQUAL, // 100% equal
    DRILL_DOWN_NEEDED, // element types are equal, but elements are composite
    TYPE_ONLY, // only element types are equal
    NOT_EQUAL, // 100% different
  }

  @NotNull
  private static <OT, NT> DiffTreeChangeBuilder<OT, NT> emptyConsumer() {
    //noinspection unchecked
    return EMPTY_CONSUMER;
  }
  private static final DiffTreeChangeBuilder EMPTY_CONSUMER = new DiffTreeChangeBuilder() {
    @Override
    public void nodeReplaced(@NotNull Object oldChild, @NotNull Object newChild) {

    }

    @Override
    public void nodeDeleted(@NotNull Object oldParent, @NotNull Object oldNode) {

    }

    @Override
    public void nodeInserted(@NotNull Object oldParent, @NotNull Object newNode, int pos) {

    }
  };

  @NotNull
  private CompareResult build(@NotNull OT oldNode, @NotNull NT newNode, int level, @NotNull DiffTreeChangeBuilder<OT, NT> consumer) {
    if (level == myNewChildrenLists.size()) {
      myNewChildrenLists.add(new Ref<NT[]>());
      myOldChildrenLists.add(new Ref<OT[]>());
    }

    final Ref<OT[]> oldChildrenR = myOldChildrenLists.get(level);
    int oldChildrenSize = myOldTree.getChildren(oldNode, oldChildrenR);
    final OT[] oldChildren = oldChildrenR.get();

    final Ref<NT[]> newChildrenR = myNewChildrenLists.get(level);
    int newChildrenSize = myNewTree.getChildren(newNode, newChildrenR);
    final NT[] newChildren = newChildrenR.get();

    CompareResult result;
    if (Math.abs(oldChildrenSize - newChildrenSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      consumer.nodeReplaced(oldNode, newNode);
      result = CompareResult.NOT_EQUAL;
    }
    else if (oldChildrenSize == 0 && newChildrenSize == 0) {
      if (!myComparator.hashCodesEqual(oldNode, newNode) || !myComparator.typesEqual(oldNode, newNode)) {
        consumer.nodeReplaced(oldNode, newNode);
        result = CompareResult.NOT_EQUAL;
      }
      else {
        result = CompareResult.EQUAL;
      }
    }
    else {
      int minSize = Math.min(oldChildrenSize, newChildrenSize);
      int suffixLength = match(oldChildren, oldChildrenSize - 1, newChildren, newChildrenSize - 1, level, -1, minSize);
      // for equal size old and new children we have to compare one element less because it was already checked in (unsuccessful) suffix match
      int maxPrefixLength = minSize - suffixLength - (oldChildrenSize == newChildrenSize && suffixLength < minSize ? 1 : 0);
      int prefixLength = match(oldChildren, 0, newChildren, 0, level, 1, maxPrefixLength);

      if (oldChildrenSize == newChildrenSize && suffixLength + prefixLength == oldChildrenSize) {
        result = CompareResult.EQUAL;
      }
      else if (consumer == emptyConsumer()) {
        result = CompareResult.NOT_EQUAL;
      }
      else {
        int oldIndex = prefixLength;
        int newIndex = prefixLength;
        while (oldIndex < oldChildrenSize - suffixLength || newIndex < newChildrenSize - suffixLength) {
          ThreeElementMatchResult vicinityMatch = matchNext3Children(oldChildren, newChildren, oldIndex, newIndex,
                                                                          oldChildrenSize - suffixLength,
                                                                          newChildrenSize - suffixLength);
          if (vicinityMatch.hasStartMatch()) {
            if (vicinityMatch == ThreeElementMatchResult.drillDownStartMatch) {
              build(oldChildren[oldIndex], newChildren[newIndex], level + 1, consumer);
            }
            else if (vicinityMatch == ThreeElementMatchResult.replaceStart) {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
            }
            oldIndex++; newIndex++;
          }
          else if (vicinityMatch != ThreeElementMatchResult.noMatch) {
            for (int i = vicinityMatch.skipOldCount() - 1; i >= 0; i--) {
              consumer.nodeDeleted(oldNode, oldChildren[oldIndex]);
              oldIndex++;
            }
            for (int i = vicinityMatch.skipNewCount() - 1; i >= 0; i--) {
              consumer.nodeInserted(oldNode, newChildren[newIndex], newIndex);
              newIndex++;
            }
          }
          else {
            // last resort: maybe the last elements are more similar?
            int suffixMatch = matchLastChildren(level, consumer,
                                                oldChildrenSize - suffixLength, oldChildren, oldIndex,
                                                newChildrenSize - suffixLength, newChildren, newIndex);
            if (suffixMatch > 0) {
              suffixLength += suffixMatch;
            } else {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
              oldIndex++;newIndex++;
            }
          }
        }
        result = CompareResult.NOT_EQUAL;
      }
    }
    myOldTree.disposeChildren(oldChildren, oldChildrenSize);
    myNewTree.disposeChildren(newChildren, newChildrenSize);
    return result;
  }

  private ThreeElementMatchResult matchNext3Children(OT[] oldChildren, NT[] newChildren, int oldIndex, int newIndex, int oldLimit, int newLimit) {
    if (oldIndex >= oldLimit) return ThreeElementMatchResult.skipNew1;
    if (newIndex >= newLimit) return ThreeElementMatchResult.skipOld1;
    
    OT oldChild1 = oldChildren[oldIndex];
    NT newChild1 = newChildren[newIndex];

    CompareResult c11 = looksEqual(oldChild1, newChild1);
    if (c11 == CompareResult.EQUAL) return ThreeElementMatchResult.fullStartMatch;
    if (c11 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.drillDownStartMatch;

    OT oldChild2 = oldIndex < oldLimit - 1 ? oldChildren[oldIndex + 1] : null;
    NT newChild2 = newIndex < newLimit - 1 ? newChildren[newIndex + 1] : null;

    CompareResult c12 = looksEqual(oldChild1, newChild2);
    if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.skipNew1;

    CompareResult c21 = looksEqual(oldChild2, newChild1);
    if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.skipOld1;

    if (c11 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.replaceStart;

    if (c12 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.skipNew1;
    if (c21 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.skipOld1;

    // check that maybe two children are inserted/deleted
    // (which frequently is a case when e.g. a PsiMethod inserted, the trailing PsiWhiteSpace is appended too)
    OT oldChild3 = oldIndex < oldLimit - 2 ? oldChildren[oldIndex + 2] : null;
    NT newChild3 = newIndex < newLimit - 2 ? newChildren[newIndex + 2] : null;

    if (looksEqual(oldChild1, newChild3) != CompareResult.NOT_EQUAL) return ThreeElementMatchResult.skipNew2;
    if (looksEqual(oldChild3, newChild1) != CompareResult.NOT_EQUAL) return ThreeElementMatchResult.skipOld2;
    
    return ThreeElementMatchResult.noMatch;
  }

  private enum ThreeElementMatchResult {
    fullStartMatch, drillDownStartMatch, replaceStart,
    skipNew1, skipNew2,
    skipOld1, skipOld2,
    noMatch;

    final int skipNewCount() { return this == skipNew1 ? 1 : this == skipNew2 ? 2 : 0; }
    final int skipOldCount() { return this == skipOld1 ? 1 : this == skipOld2 ? 2 : 0; }
    final boolean hasStartMatch() { return this == fullStartMatch || this == drillDownStartMatch || this == replaceStart; }
  }

  private int matchLastChildren(int level, DiffTreeChangeBuilder<OT, NT> consumer,
                                int oldChildrenLimit, OT[] oldChildren, int oldIndex,
                                int newChildrenLimit, NT[] newChildren, int newIndex) {
    int len = 0;
    while (oldIndex < oldChildrenLimit - len && newIndex < newChildrenLimit - len) {
      OT oldLastChild = oldChildren[oldChildrenLimit - len - 1];
      NT newLastChild = newChildren[newChildrenLimit - len - 1];
      CompareResult c = looksEqual(oldLastChild, newLastChild);
      if (c == CompareResult.NOT_EQUAL) break;

      if (c == CompareResult.DRILL_DOWN_NEEDED) {
        build(oldLastChild, newLastChild, level + 1, consumer);
      }
      else if (c == CompareResult.TYPE_ONLY) {
        consumer.nodeReplaced(oldLastChild, newLastChild);
      }
      len++;
    }
    return len;
  }

  // tries to match as many nodes as possible from the beginning (if step=1) of from the end (if step =-1)
  // returns number of nodes matched
  private int match(OT[] oldChildren,
                    int oldIndex,
                    NT[] newChildren,
                    int newIndex,
                    int level,
                    int step, // 1 if we go from the start to the end; -1 if we go from the end to the start
                    int maxLength) {
    int delta = 0;
    while (delta != maxLength*step) {
      OT oldChild = oldChildren[oldIndex + delta];
      NT newChild = newChildren[newIndex + delta];

      CompareResult c11 = looksEqual(oldChild, newChild);

      if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
        c11 = textMatch(oldChild, newChild) ? build(oldChild, newChild, level + 1, DiffTree.<OT, NT>emptyConsumer()) : CompareResult.NOT_EQUAL;
        assert c11 != CompareResult.DRILL_DOWN_NEEDED;
      }
      if (c11 != CompareResult.EQUAL) {
        break;
      }
      delta += step;
    }
    return delta*step;
  }

  private boolean textMatch(OT oldChild, NT newChild) {
    int oldStart = myOldTree.getStartOffset(oldChild) - myOldTreeStart;
    int oldEnd = myOldTree.getEndOffset(oldChild) - myOldTreeStart;
    int newStart = myNewTree.getStartOffset(newChild) - myNewTreeStart;
    int newEnd = myNewTree.getEndOffset(newChild) - myNewTreeStart;
    // drill down only if node texts match, but when they do, match all the way down unconditionally
    return CharArrayUtil.regionMatches(myOldText, oldStart, oldEnd, myNewText, newStart, newEnd);
  }

  @NotNull
  private CompareResult looksEqual(OT oldChild1, NT newChild1) {
    if (oldChild1 == null || newChild1 == null || !myComparator.typesEqual(oldChild1, newChild1)) return CompareResult.NOT_EQUAL;
    ThreeState ret = myComparator.deepEqual(oldChild1, newChild1);
    if (ret == ThreeState.YES) return CompareResult.EQUAL;
    if (ret == ThreeState.UNSURE) return CompareResult.DRILL_DOWN_NEEDED;
    return CompareResult.TYPE_ONLY;
  }
}
