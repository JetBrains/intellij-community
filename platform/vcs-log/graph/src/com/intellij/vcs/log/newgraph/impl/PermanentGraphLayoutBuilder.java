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

import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PermanentGraphLayoutBuilder {

  @NotNull
  public static PermanentGraphLayout build(@NotNull DfsUtil dfsUtil,
                                           @NotNull PermanentGraph graph,
                                           @NotNull Comparator<Integer> compareTwoHeaderNodeIndex) {
    List<Integer> heads = new ArrayList<Integer>();
    for (int i = 0; i < graph.nodesCount(); i++) {
      if (graph.getUpNodes(i).size() == 0) {
        heads.add(i);
      }
    }
    Collections.sort(heads, compareTwoHeaderNodeIndex);
    PermanentGraphLayoutBuilder builder = new PermanentGraphLayoutBuilder(graph, heads, dfsUtil);
    return builder.build();
  }

  private final PermanentGraph myGraph;
  private final int[] myLayoutIndex;

  private final List<Integer> myHeadNodeIndex;
  private final int[] myStartLayoutIndexForHead;

  private final DfsUtil myDfsUtil;

  private int currentLayoutIndex = 1;

  private PermanentGraphLayoutBuilder(PermanentGraph graph, List<Integer> headNodeIndex, DfsUtil dfsUtil) {
    myGraph = graph;
    myDfsUtil = dfsUtil;
    myLayoutIndex = new int[graph.nodesCount()];

    myHeadNodeIndex = headNodeIndex;
    myStartLayoutIndexForHead = new int[headNodeIndex.size()];

  }

  private void dfs(int nodeIndex) {
    myDfsUtil.nodeDfsIterator(nodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        boolean firstVisit = myLayoutIndex[currentNode] == 0;
        if (firstVisit)
          myLayoutIndex[currentNode] = currentLayoutIndex;

        int childWithoutLayoutIndex = -1;
        for (int childNodeIndex : myGraph.getDownNodes(currentNode)) {
          if (childNodeIndex != SomeGraph.NOT_LOAD_COMMIT && myLayoutIndex[childNodeIndex] == 0) {
            childWithoutLayoutIndex = childNodeIndex;
            break;
          }
        }

        if (childWithoutLayoutIndex == -1) {
          if (firstVisit)
            currentLayoutIndex++;

          return DfsUtil.NextNode.NODE_NOT_FOUND;
        } else {
          return childWithoutLayoutIndex;
        }
      }
    });
  }

  @NotNull
  private PermanentGraphLayout build() {
    for(int i = 0; i < myHeadNodeIndex.size(); i++) {
      int headNodeIndex = myHeadNodeIndex.get(i);
      myStartLayoutIndexForHead[i] = currentLayoutIndex;

      dfs(headNodeIndex);
    }

    return new PermanentGraphLayoutImpl(myLayoutIndex, myHeadNodeIndex, myStartLayoutIndexForHead);
  }




  private static class PermanentGraphLayoutImpl implements PermanentGraphLayout {
    private final int[] myLayoutIndex;

    private final List<Integer> myHeadNodeIndex;
    private final int[] myStartLayoutIndexForHead;

    private PermanentGraphLayoutImpl(int[] layoutIndex, List<Integer> headNodeIndex, int[] startLayoutIndexForHead) {
      myLayoutIndex = layoutIndex;
      myHeadNodeIndex = headNodeIndex;
      myStartLayoutIndexForHead = startLayoutIndexForHead;
    }

    @Override
    public int getLayoutIndex(int nodeIndex) {
      return myLayoutIndex[nodeIndex];
    }

    @Override
    public int getOneOfHeadNodeIndex(int nodeIndex) {
      return getHeadNodeIndex(getLayoutIndex(nodeIndex));
    }

    @Override
    public int getHeadNodeIndex(int layoutIndex) {
      return myHeadNodeIndex.get(getHeadOrder(layoutIndex));
    }

    private int getHeadOrder(int layoutIndex) {
      int a = 0;
      int b = myStartLayoutIndexForHead.length - 1;
      while (b > a) {
        int middle = (a + b + 1) / 2;
        if (myStartLayoutIndexForHead[middle] <= layoutIndex)
          a = middle;
        else
          b = middle - 1;
      }
      return a;
    }

    @Override
    public int getStartLayout(int layoutIndex) {
      return myStartLayoutIndexForHead[getHeadOrder(layoutIndex)];
    }
  }
}
