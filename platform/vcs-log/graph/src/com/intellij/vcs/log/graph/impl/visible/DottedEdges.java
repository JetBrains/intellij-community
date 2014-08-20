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
package com.intellij.vcs.log.graph.impl.visible;

import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DottedEdges {

  @NotNull
  public static DottedEdges newInstance(@NotNull MultiMap<Integer, Integer> delegate) {
    int[] nodesWithEdges = ArrayUtil.toIntArray(delegate.keySet());
    Arrays.sort(nodesWithEdges);

    int[] startIndexes = new int[nodesWithEdges.length + 1];
    int[] edges = new int[delegate.values().size()];

    int start = 0;
    for (int i = 0; i < startIndexes.length - 1; i++) {
      startIndexes[i] = start;
      for (int toNode : delegate.get(nodesWithEdges[i])) {
        edges[start] = toNode;
        start++;
      }
    }
    startIndexes[startIndexes.length - 1] = start;

    return new DottedEdges(nodesWithEdges, startIndexes, edges);
  }

  @NotNull private final int[] sortedStartNodes; // graph is not oriented => end nodes are there as well

  @NotNull private final int[] startEdgesPosition;

  @NotNull private final int[] endNodes;

  public DottedEdges(@NotNull int[] sortedStartNodes, @NotNull int[] startEdgesPosition, @NotNull int[] endNodes) {
    this.sortedStartNodes = sortedStartNodes;
    this.startEdgesPosition = startEdgesPosition;
    this.endNodes = endNodes;
  }

  public List<Integer> getAdjacentNodes(int nodeIndex) {
    int smallIndex = Arrays.binarySearch(sortedStartNodes, nodeIndex);
    if (smallIndex < 0)
      return Collections.emptyList();
    List<Integer> result = new SmartList<Integer>();

    for (int i = startEdgesPosition[smallIndex]; i < startEdgesPosition[smallIndex  + 1]; i++)
      result.add(endNodes[i]);
    return result;
  }

}
