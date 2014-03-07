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

package com.intellij.vcs.log.newgraph.impl;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

public class PermanentGraphImpl implements PermanentGraph {
  private final Flags mySimpleNodes;
  private final int[] myNodeToHashIndex;

  // myNodeToEdgeIndex.length = nodesCount() + 1. See adjacentNodes().
  private final int[] myNodeToEdgeIndex;
  private final int[] myLongEdges;

  /*package*/ PermanentGraphImpl(Flags simpleNodes, int[] nodeToHashIndex, int[] nodeToEdgeIndex, int[] longEdges) {
    mySimpleNodes = simpleNodes;
    myNodeToHashIndex = nodeToHashIndex;
    myNodeToEdgeIndex = nodeToEdgeIndex;
    myLongEdges = longEdges;
  }

  @Override
  public int nodesCount() {
    return mySimpleNodes.size();
  }

  @NotNull
  private List<Integer> adjacentNodes(final int nodeIndex) {
    final int startIndex = myNodeToEdgeIndex[nodeIndex];

    return new AbstractList<Integer>() {
      @Override
      public Integer get(int index) {
        return myLongEdges[startIndex + index];
      }

      @Override
      public int size() {
          return myNodeToEdgeIndex[nodeIndex + 1] - startIndex;
      }
    };
  }

  @NotNull
  @Override
  public List<Integer> getUpNodes(int nodeIndex) {
    List<Integer> result = new SmartList<Integer>();
    if (nodeIndex != 0 && mySimpleNodes.get(nodeIndex - 1)) {
      result.add(nodeIndex - 1);
    }

    for (Integer node: adjacentNodes(nodeIndex)) {
      if (node < nodeIndex)
        result.add(node);
    }

    return result;
  }


  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    if (mySimpleNodes.get(nodeIndex)) {
      return Collections.singletonList(nodeIndex + 1);
    }

    List<Integer> result = new SmartList<Integer>();
    for (Integer node: adjacentNodes(nodeIndex)) {
      if (nodeIndex < node)
        result.add(node);
    }

    return result;
  }

  @Override
  public int getHashIndex(int nodeIndex) {
    if (nodeIndex == nodesCount())
      return NOT_LOAD_COMMIT;
    return myNodeToHashIndex[nodeIndex];
  }
}
