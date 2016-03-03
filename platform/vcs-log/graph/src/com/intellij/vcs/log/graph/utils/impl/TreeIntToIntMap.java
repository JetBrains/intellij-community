/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.util.BooleanFunction;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import org.jetbrains.annotations.NotNull;

public class TreeIntToIntMap extends AbstractIntToIntMap implements UpdatableIntToIntMap {

  public static UpdatableIntToIntMap newInstance(@NotNull final BooleanFunction<Integer> thisIsVisible, final int longSize) {
    if (longSize < 0) throw new NegativeArraySizeException("size < 0: " + longSize);

    if (longSize == 0) return IDIntToIntMap.EMPTY;

    int countLevels; // longSize -> countLevels: 1..2 -> 2; 3..4 -> 3;  5..8 -> 4
    if (longSize == 1) {
      countLevels = 2;
    }
    else {
      countLevels = countDigits(longSize - 1) + 1;
    }

    int[] emptyTree = new int[(1 << (countLevels - 1))];
    TreeIntToIntMap intToIntMap = new TreeIntToIntMap(thisIsVisible, longSize, countLevels, emptyTree);
    intToIntMap.update(0, longSize - 1);
    return intToIntMap;
  }

  private static int countDigits(int longSize) {
    int count = 0;
    while (longSize != 0) {
      count++;
      longSize >>= 1;
    }
    return count;
  }

  @NotNull private final BooleanFunction<Integer> myThisIsVisible;

  private final int myLongSize;
  private final int myCountLevels;
  private final int[] myTree;

  private TreeIntToIntMap(@NotNull BooleanFunction<Integer> thisIsVisible, int longSize, int countLevels, int[] tree) {
    myThisIsVisible = thisIsVisible;
    myLongSize = longSize;
    myCountLevels = countLevels;
    myTree = tree;
  }


  @Override
  public int shortSize() {
    return myTree[1];
  }

  @Override
  public int longSize() {
    return myLongSize;
  }

  @Override
  public int getLongIndex(int shortIndex) {
    checkShortIndex(shortIndex);

    int node = 1;
    for (int level = 0; level < myCountLevels - 1; level++) {
      int child = node << 1;
      int countInChildNode = getCountInNode(child);
      if (countInChildNode > shortIndex) {
        node = child;
      }
      else {
        node = child + 1;
        shortIndex -= countInChildNode;
      }
    }
    return node - myTree.length;
  }

  @Override
  public void update(int startLongIndex, int endLongIndex) {
    checkUpdateParameters(startLongIndex, endLongIndex);

    int startNode = startLongIndex + myTree.length;
    int endNode = endLongIndex + myTree.length;
    int commonNode = startNode >> countDigits(startNode ^ endNode);
    updateNodeCount(commonNode);

    int parent = commonNode >> 1;
    while (parent != 0) {
      myTree[parent] = getCountInNode(parent << 1) + getCountInNode((parent << 1) + 1);
      parent >>= 1;
    }
  }

  private boolean isLastLevel(int node) {
    return node >= myTree.length;
  }

  private int updateNodeCount(int node) {
    if (isLastLevel(node)) return getCountInLastLevel(node);

    int child = node << 1;
    myTree[node] = updateNodeCount(child) + updateNodeCount(child + 1);
    return myTree[node];
  }

  private int getCountInLastLevel(int node) {
    node -= myTree.length;
    if (node < myLongSize && myThisIsVisible.fun(node)) {
      return 1;
    }
    else {
      return 0;
    }
  }

  private int getCountInNode(int node) {
    if (isLastLevel(node)) {
      return getCountInLastLevel(node);
    }
    else {
      return myTree[node];
    }
  }
}
