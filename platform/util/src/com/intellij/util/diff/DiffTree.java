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

package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;

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
  private final DiffTreeChangeBuilder<OT, NT> myConsumer;
  private final List<Ref<OT[]>> myOldChildrenLists = new ArrayList<Ref<OT[]>>();
  private final List<Ref<NT[]>> myNewChildrenLists = new ArrayList<Ref<NT[]>>();

  private DiffTree(final FlyweightCapableTreeStructure<OT> oldTree,
                   final FlyweightCapableTreeStructure<NT> newTree,
                   final ShallowNodeComparator<OT, NT> comparator,
                   final DiffTreeChangeBuilder<OT, NT> consumer) {

    myOldTree = oldTree;
    myNewTree = newTree;
    myComparator = comparator;
    myConsumer = consumer;
  }

  public static <OT, NT> void diff(final FlyweightCapableTreeStructure<OT> oldTree,
                                   final FlyweightCapableTreeStructure<NT> newTree,
                                   final ShallowNodeComparator<OT, NT> comparator,
                                   final DiffTreeChangeBuilder<OT, NT> consumer) {
    new DiffTree<OT, NT>(oldTree, newTree, comparator, consumer).build(oldTree.getRoot(), newTree.getRoot(), 0);
  }

  private static enum CompareResult {
    EQUAL, // 100% equal
    DRILL_DOWN_NEEDED, // element types are equal, but elements are composite
    TYPE_ONLY, // only element types are equal
    NOT_EQUAL, // 100% different
  }

  private void build(OT oldN, NT newN, int level) {
    OT oldNode = myOldTree.prepareForGetChildren(oldN);
    NT newNode = myNewTree.prepareForGetChildren(newN);

    if (level >= myNewChildrenLists.size()) {
      myNewChildrenLists.add(new Ref<NT[]>());
      myOldChildrenLists.add(new Ref<OT[]>());
    }

    final Ref<OT[]> oldChildrenR = myOldChildrenLists.get(level);
    int oldSize = myOldTree.getChildren(oldNode, oldChildrenR);
    final OT[] oldChildren = oldChildrenR.get();

    final Ref<NT[]> newChildrenR = myNewChildrenLists.get(level);
    int newSize = myNewTree.getChildren(newNode, newChildrenR);
    final NT[] newChildren = newChildrenR.get();

    compareLevel(level, oldNode, oldSize, oldChildren, newNode, newSize, newChildren);
    disposeLevel(oldChildren, oldSize, newChildren, newSize);
  }

  private void compareLevel(int level, OT oldNode, int oldSize, OT[] oldChildren, NT newNode, int newSize, NT[] newChildren) {
    if (Math.abs(oldSize - newSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      myConsumer.nodeReplaced(oldNode, newNode);
      return;
    }

    final ShallowNodeComparator<OT, NT> comparator = myComparator;
    if (oldSize == 0 && newSize == 0) {
      if (!comparator.hashCodesEqual(oldNode, newNode) || !comparator.typesEqual(oldNode, newNode)) {
        myConsumer.nodeReplaced(oldNode, newNode);
      }
      return;
    }

    while (oldSize > 0 && newSize > 0) {
      OT oldChild1 = oldChildren[oldSize-1];
      NT newChild1 = newChildren[newSize-1];

      CompareResult c11 = looksEqual(comparator, oldChild1, newChild1);

      if (c11 != CompareResult.EQUAL && c11 != CompareResult.DRILL_DOWN_NEEDED) {
        break;
      }
      if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
        build(oldChild1, newChild1, level + 1);
      }
      oldSize--;
      newSize--;
    }

    int oldIndex = 0;
    int newIndex = 0;
    while (oldIndex < oldSize || newIndex < newSize) {
      OT oldChild1 = oldIndex < oldSize ? oldChildren[oldIndex] : null;
      OT oldChild2 = oldIndex < oldSize-1 ? oldChildren[oldIndex+1] : null;
      NT newChild1 = newIndex < newSize ? newChildren[newIndex] : null;
      NT newChild2 = newIndex < newSize-1 ? newChildren[newIndex+1] : null;

      CompareResult c11 = looksEqual(comparator, oldChild1, newChild1);

      if (c11 == CompareResult.EQUAL || c11 == CompareResult.DRILL_DOWN_NEEDED) {
        if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
          build(oldChild1, newChild1, level+1);
        }
        oldIndex++;
        newIndex++;
        continue;
      }
      CompareResult c12 = looksEqual(comparator, oldChild1, newChild2);
      CompareResult c21 = looksEqual(comparator, oldChild2, newChild1);
      if (c11 == CompareResult.TYPE_ONLY) {
        if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED) {
          myConsumer.nodeDeleted(oldNode, oldChild1);
          oldIndex++;
          continue;
        }
        else if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) {
          myConsumer.nodeInserted(oldNode, newChild1, newIndex);
          newIndex++;
          continue;
        }
        else {
          myConsumer.nodeReplaced(oldChild1, newChild1);
          oldIndex++;
          newIndex++;
          continue;
        }
      }
      if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED || c12 == CompareResult.TYPE_ONLY) {
        myConsumer.nodeInserted(oldNode, newChild1, newIndex);
        newIndex++;
        continue;
      }

      if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED || c21 == CompareResult.TYPE_ONLY) {
        myConsumer.nodeDeleted(oldNode, oldChild1);
        oldIndex++;
        continue;
      }

      if (oldChild1 == null) {
        myConsumer.nodeInserted(oldNode, newChild1, newIndex);
        newIndex++;
        continue;
      }
      if (newChild1 == null) {
        myConsumer.nodeDeleted(oldNode, oldChild1);
        oldIndex++;
        continue;
      }
      myConsumer.nodeReplaced(oldChild1, newChild1);
      oldIndex++;
      newIndex++;
    }
  }

  private CompareResult looksEqual(ShallowNodeComparator<OT, NT> comparator, OT oldChild1, NT newChild1) {
    if (oldChild1 == null || newChild1 == null) {
      return oldChild1 == newChild1 ? CompareResult.EQUAL : CompareResult.NOT_EQUAL;
    }
    if (!comparator.typesEqual(oldChild1, newChild1)) return CompareResult.NOT_EQUAL;
    ThreeState ret = comparator.deepEqual(oldChild1, newChild1);
    if (ret == ThreeState.UNSURE) return CompareResult.DRILL_DOWN_NEEDED;
    if (ret == ThreeState.YES) return CompareResult.EQUAL;
    return CompareResult.TYPE_ONLY;
  }

  private void disposeLevel(final OT[] oldChildren, final int oldSize, final NT[] newChildren, final int newSize) {
    myOldTree.disposeChildren(oldChildren, oldSize);
    myNewTree.disposeChildren(newChildren, newSize);
  }
}
