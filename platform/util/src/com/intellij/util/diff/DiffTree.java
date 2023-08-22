// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class DiffTree<OT, NT> {
  private static final int CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD = 20;

  private final FlyweightCapableTreeStructure<OT> myOldTree;
  private final FlyweightCapableTreeStructure<NT> myNewTree;
  private final ShallowNodeComparator<? super OT, ? super NT> myComparator;
  private final List<Ref<OT[]>> myOldChildrenLists = new ArrayList<>();
  private final List<Ref<NT[]>> myNewChildrenLists = new ArrayList<>();
  private final CharSequence myOldText;
  private final CharSequence myNewText;
  private final int myOldTreeStart;
  private final int myNewTreeStart;

  private DiffTree(@NotNull FlyweightCapableTreeStructure<OT> oldTree,
                   @NotNull FlyweightCapableTreeStructure<NT> newTree,
                   @NotNull ShallowNodeComparator<? super OT, ? super NT> comparator,
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
                                   @NotNull ShallowNodeComparator<? super OT, ? super NT> comparator,
                                   @NotNull DiffTreeChangeBuilder<? super OT, ? super NT> consumer,
                                   @NotNull CharSequence oldText) {
    DiffTree<OT, NT> tree = new DiffTree<>(oldTree, newTree, comparator, oldText);
    tree.build(oldTree.getRoot(), newTree.getRoot(), 0, consumer);
  }

  private enum CompareResult {
    EQUAL, // 100% equal
    DRILL_DOWN_NEEDED, // element types are equal, but elements are composite
    TYPE_ONLY, // only element types are equal
    NOT_EQUAL, // 100% different
  }

  private static @NotNull <OT, NT> DiffTreeChangeBuilder<OT, NT> emptyConsumer() {
    //noinspection unchecked
    return (DiffTreeChangeBuilder<OT, NT>)EMPTY_CONSUMER;
  }

  private static final DiffTreeChangeBuilder<?,?> EMPTY_CONSUMER = new DiffTreeChangeBuilder<Object,Object>() {
    @Override
    public void nodeReplaced(@NotNull Object oldChild, @NotNull Object newChild) { }

    @Override
    public void nodeDeleted(@NotNull Object oldParent, @NotNull Object oldNode) { }

    @Override
    public void nodeInserted(@NotNull Object oldParent, @NotNull Object newNode, int pos) { }
  };

  private @NotNull CompareResult build(@NotNull OT oldNode, @NotNull NT newNode, int level, @NotNull DiffTreeChangeBuilder<? super OT, ? super NT> consumer) {
    if (level == myNewChildrenLists.size()) {
      myNewChildrenLists.add(new Ref<>());
      myOldChildrenLists.add(new Ref<>());
    }

    Ref<OT[]> oldChildrenR = myOldChildrenLists.get(level);
    int oldChildrenSize = myOldTree.getChildren(oldNode, oldChildrenR);
    OT[] oldChildren = oldChildrenR.get();

    Ref<NT[]> newChildrenR = myNewChildrenLists.get(level);
    int newChildrenSize = myNewTree.getChildren(newNode, newChildrenR);
    NT[] newChildren = newChildrenR.get();

    CompareResult result;
    if (Math.abs(oldChildrenSize - newChildrenSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      consumer.nodeReplaced(oldNode, newNode);
      result = CompareResult.NOT_EQUAL;
    }
    else if (oldChildrenSize == 0 && newChildrenSize == 0) {
      if (!myComparator.typesEqual(oldNode, newNode) || !myComparator.hashCodesEqual(oldNode, newNode)) {
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
            if (vicinityMatch == ThreeElementMatchResult.DRILL_DOWN_START_MATCH) {
              build(oldChildren[oldIndex], newChildren[newIndex], level + 1, consumer);
            }
            else if (vicinityMatch == ThreeElementMatchResult.REPLACE_START) {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
            }
            oldIndex++;
            newIndex++;
          }
          else if (vicinityMatch != ThreeElementMatchResult.NO_MATCH) {
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
            }
            else {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
              oldIndex++;
              newIndex++;
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
    if (oldIndex >= oldLimit) return ThreeElementMatchResult.SKIP_NEW_1;
    if (newIndex >= newLimit) return ThreeElementMatchResult.SKIP_OLD_1;

    OT oldChild1 = oldChildren[oldIndex];
    NT newChild1 = newChildren[newIndex];

    CompareResult c11 = looksEqual(oldChild1, newChild1);
    if (c11 == CompareResult.EQUAL) return ThreeElementMatchResult.FULL_START_MATCH;
    if (c11 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.DRILL_DOWN_START_MATCH;

    OT oldChild2 = oldIndex < oldLimit - 1 ? oldChildren[oldIndex + 1] : null;
    NT newChild2 = newIndex < newLimit - 1 ? newChildren[newIndex + 1] : null;

    CompareResult c12 = looksEqual(oldChild1, newChild2);
    if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.SKIP_NEW_1;

    CompareResult c21 = looksEqual(oldChild2, newChild1);
    if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.SKIP_OLD_1;

    if (c11 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.REPLACE_START;

    if (c12 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.SKIP_NEW_1;
    if (c21 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.SKIP_OLD_1;

    // check whether two children are inserted/deleted
    // (which frequently is a case when e.g. a PsiMethod inserted, the trailing PsiWhiteSpace is appended too)
    OT oldChild3 = oldIndex < oldLimit - 2 ? oldChildren[oldIndex + 2] : null;
    NT newChild3 = newIndex < newLimit - 2 ? newChildren[newIndex + 2] : null;

    if (looksEqual(oldChild1, newChild3) != CompareResult.NOT_EQUAL) return ThreeElementMatchResult.SKIP_NEW_2;
    if (looksEqual(oldChild3, newChild1) != CompareResult.NOT_EQUAL) return ThreeElementMatchResult.SKIP_OLD_2;

    return ThreeElementMatchResult.NO_MATCH;
  }

  // Represents the result of matching among 3 next node children in before and after tree
  private enum ThreeElementMatchResult {
    // first children match completely
    FULL_START_MATCH,
    // first children match well, PSI instance should be preserved
    DRILL_DOWN_START_MATCH,
    // PSI instance should be replaced for first children
    REPLACE_START,
    // first 1 or 2 "new" children don't match, report them as inserted and try matching after them
    SKIP_NEW_1, SKIP_NEW_2,
    // first 1 or 2 "old" children don't match, report them as deleted and try matching after them
    SKIP_OLD_1, SKIP_OLD_2,
    // nothing in the 3-children scope matches both the "old" and the "old" first child
    NO_MATCH;

    int skipNewCount() { return this == SKIP_NEW_1 ? 1 : this == SKIP_NEW_2 ? 2 : 0; }
    int skipOldCount() { return this == SKIP_OLD_1 ? 1 : this == SKIP_OLD_2 ? 2 : 0; }
    boolean hasStartMatch() { return this == FULL_START_MATCH || this == DRILL_DOWN_START_MATCH || this == REPLACE_START; }
  }

  private int matchLastChildren(int level, DiffTreeChangeBuilder<? super OT, ? super NT> consumer,
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
        c11 = textMatch(oldChild, newChild) ? build(oldChild, newChild, level + 1, emptyConsumer()) : CompareResult.NOT_EQUAL;
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

  private @NotNull CompareResult looksEqual(OT oldChild1, NT newChild1) {
    if (oldChild1 == null || newChild1 == null || !myComparator.typesEqual(oldChild1, newChild1)) return CompareResult.NOT_EQUAL;
    ThreeState ret = myComparator.deepEqual(oldChild1, newChild1);
    if (ret == ThreeState.YES) return CompareResult.EQUAL;
    if (ret == ThreeState.UNSURE) return CompareResult.DRILL_DOWN_NEEDED;
    return CompareResult.TYPE_ONLY;
  }
}