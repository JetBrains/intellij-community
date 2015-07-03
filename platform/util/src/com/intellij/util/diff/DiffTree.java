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
                   @NotNull ShallowNodeComparator<OT, NT> comparator) {
    myOldTree = oldTree;
    myNewTree = newTree;
    myComparator = comparator;
    myOldText = oldTree.toString(oldTree.getRoot());
    myOldTreeStart = oldTree.getStartOffset(oldTree.getRoot());
    myNewText = newTree.toString(newTree.getRoot());
    myNewTreeStart = newTree.getStartOffset(newTree.getRoot());
  }

  public static <OT, NT> void diff(@NotNull FlyweightCapableTreeStructure<OT> oldTree,
                                   @NotNull FlyweightCapableTreeStructure<NT> newTree,
                                   @NotNull ShallowNodeComparator<OT, NT> comparator,
                                   @NotNull DiffTreeChangeBuilder<OT, NT> consumer) {
    final DiffTree<OT, NT> tree = new DiffTree<OT, NT>(oldTree, newTree, comparator);
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
  private CompareResult build(@NotNull OT oldN, @NotNull NT newN, int level, @NotNull DiffTreeChangeBuilder<OT, NT> consumer) {
    OT oldNode = myOldTree.prepareForGetChildren(oldN);
    NT newNode = myNewTree.prepareForGetChildren(newN);

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
      final ShallowNodeComparator<OT, NT> comparator = myComparator;

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
        int oldIndex = oldChildrenSize - suffixLength - 1;
        int newIndex = newChildrenSize - suffixLength - 1;
        while (oldIndex >= prefixLength || newIndex >= prefixLength) {
          OT oldChild1 = oldIndex >= prefixLength ? oldChildren[oldIndex] : null;
          OT oldChild2 = oldIndex >= prefixLength + 1 ? oldChildren[oldIndex - 1] : null;
          OT oldChild3 = oldIndex >= prefixLength + 2 ? oldChildren[oldIndex - 2] : null;
          NT newChild1 = newIndex >= prefixLength ? newChildren[newIndex] : null;
          NT newChild2 = newIndex >= prefixLength + 1 ? newChildren[newIndex - 1] : null;
          NT newChild3 = newIndex >= prefixLength + 2 ? newChildren[newIndex - 2] : null;

          CompareResult c11 = looksEqual(comparator, oldChild1, newChild1);
          if (c11 == CompareResult.EQUAL || c11 == CompareResult.DRILL_DOWN_NEEDED) {
            if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
              build(oldChild1, newChild1, level + 1, consumer);
            }
            oldIndex--;
            newIndex--;
            continue;
          }
          if (c11 == CompareResult.TYPE_ONLY) {
            CompareResult c21 = looksEqual(comparator, oldChild2, newChild1);
            if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED) {
              consumer.nodeDeleted(oldNode, oldChild1);
              oldIndex--;
              continue;
            }
            CompareResult c12 = looksEqual(comparator, oldChild1, newChild2);
            if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) {
              consumer.nodeInserted(oldNode, newChild1, oldIndex + 1);
              newIndex--;
              continue;
            }
            consumer.nodeReplaced(oldChild1, newChild1);
            oldIndex--;
            newIndex--;
            continue;
          }

          CompareResult c12 = looksEqual(comparator, oldChild1, newChild2);
          if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) {
            consumer.nodeInserted(oldNode, newChild1, oldIndex + 1);
            newIndex--;
            continue;
          }

          CompareResult c21 = looksEqual(comparator, oldChild2, newChild1);
          if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED || c21 == CompareResult.TYPE_ONLY) {
            consumer.nodeDeleted(oldNode, oldChild1);
            oldIndex--;
            continue;
          }

          if (c12 == CompareResult.TYPE_ONLY) {
            consumer.nodeInserted(oldNode, newChild1, oldIndex + 1);
            newIndex--;
            continue;
          }

          if (oldChild1 == null) {
            //consumer.nodeInserted(oldNode, newChild1, newIndex);
            consumer.nodeInserted(oldNode, newChild1, oldIndex+1);
            newIndex--;
            continue;
          }
          if (newChild1 == null) {
            consumer.nodeDeleted(oldNode, oldChild1);
            oldIndex--;
            continue;
          }

          // check that maybe two children are inserted/deleted
          // (which frequently is a case when e.g. a PsiMethod inserted, the trailing PsiWhiteSpace is appended too)
          if (oldChild3 != null || newChild3 != null) {
            CompareResult c13 = looksEqual(comparator, oldChild1, newChild3);
            if (c13 == CompareResult.EQUAL || c13 == CompareResult.DRILL_DOWN_NEEDED || c13 == CompareResult.TYPE_ONLY) {
              consumer.nodeInserted(oldNode, newChild1, oldIndex + 1);
              consumer.nodeInserted(oldNode, newChild2, oldIndex+1);
              newIndex--;
              newIndex--;
              continue;
            }
            CompareResult c31 = looksEqual(comparator, oldChild3, newChild1);
            if (c31 == CompareResult.EQUAL || c31 == CompareResult.DRILL_DOWN_NEEDED || c31 == CompareResult.TYPE_ONLY) {
              consumer.nodeDeleted(oldNode, oldChild1);
              consumer.nodeDeleted(oldNode, oldChild2);
              oldIndex--;
              oldIndex--;
              continue;
            }
          }

          // last resort: maybe the first elements are more similar?
          OT oldFirstChild = oldIndex >= prefixLength ? oldChildren[prefixLength] : null;
          NT newFirstChild = newIndex >= prefixLength ? newChildren[prefixLength] : null;
          CompareResult c = oldFirstChild == null || newFirstChild == null ? CompareResult.NOT_EQUAL : looksEqual(comparator, oldFirstChild, newFirstChild);
          if (c == CompareResult.EQUAL || c == CompareResult.TYPE_ONLY || c == CompareResult.DRILL_DOWN_NEEDED) {
            if (c == CompareResult.DRILL_DOWN_NEEDED) {
              build(oldFirstChild, newFirstChild, level + 1, consumer);
            }
            else {
              consumer.nodeReplaced(oldFirstChild, newFirstChild);
            }
            prefixLength++;
            continue;
          }

          consumer.nodeReplaced(oldChild1, newChild1);
          oldIndex--;
          newIndex--;
        }
        result = CompareResult.NOT_EQUAL;
      }
    }
    myOldTree.disposeChildren(oldChildren, oldChildrenSize);
    myNewTree.disposeChildren(newChildren, newChildrenSize);
    return result;
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

      CompareResult c11 = looksEqual(myComparator, oldChild, newChild);

      if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
        int oldStart = myOldTree.getStartOffset(oldChild) - myOldTreeStart;
        int oldEnd = myOldTree.getEndOffset(oldChild) - myOldTreeStart;
        int newStart = myNewTree.getStartOffset(newChild) - myNewTreeStart;
        int newEnd = myNewTree.getEndOffset(newChild) - myNewTreeStart;
        // drill down only if node texts match, but when they do, match all the way down unconditionally
        c11 = CharArrayUtil.regionMatches(myOldText, oldStart, oldEnd, myNewText, newStart, newEnd)
              ? build(oldChild, newChild, level + 1, DiffTree.<OT, NT>emptyConsumer())
              : CompareResult.NOT_EQUAL;
      }
      assert c11 != CompareResult.DRILL_DOWN_NEEDED;
      if (c11 != CompareResult.EQUAL) {
        break;
      }
      delta += step;
    }
    return delta*step;
  }

  @NotNull
  private static <OT, NT> CompareResult looksEqual(@NotNull ShallowNodeComparator<OT, NT> comparator, OT oldChild1, NT newChild1) {
    if (oldChild1 == null || newChild1 == null) {
      return oldChild1 == newChild1 ? CompareResult.EQUAL : CompareResult.NOT_EQUAL;
    }
    if (!comparator.typesEqual(oldChild1, newChild1)) return CompareResult.NOT_EQUAL;
    ThreeState ret = comparator.deepEqual(oldChild1, newChild1);
    if (ret == ThreeState.YES) return CompareResult.EQUAL;
    if (ret == ThreeState.UNSURE) return CompareResult.DRILL_DOWN_NEEDED;
    return CompareResult.TYPE_ONLY;
  }
}
