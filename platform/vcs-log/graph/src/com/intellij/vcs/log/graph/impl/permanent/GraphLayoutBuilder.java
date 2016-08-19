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

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

public class GraphLayoutBuilder {

  private static final Logger LOG = Logger.getInstance(GraphLayoutBuilder.class);

  @NotNull
  public static GraphLayoutImpl build(@NotNull LinearGraph graph, @NotNull Comparator<Integer> headNodeIndexComparator) {
    List<Integer> heads = new ArrayList<>();
    for (int i = 0; i < graph.nodesCount(); i++) {
      if (getUpNodes(graph, i).size() == 0) {
        heads.add(i);
      }
    }
    try {
      heads = ContainerUtil.sorted(heads, headNodeIndexComparator);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      // protection against possible comparator flaws
      LOG.error(e);
    }
    GraphLayoutBuilder builder = new GraphLayoutBuilder(graph, heads);
    return builder.build();
  }

  @NotNull private final LinearGraph myGraph;
  @NotNull private final int[] myLayoutIndex;

  @NotNull private final List<Integer> myHeadNodeIndex;
  @NotNull private final int[] myStartLayoutIndexForHead;

  @NotNull private final DfsUtil myDfsUtil = new DfsUtil();

  private int currentLayoutIndex = 1;

  private GraphLayoutBuilder(@NotNull LinearGraph graph, @NotNull List<Integer> headNodeIndex) {
    myGraph = graph;
    myLayoutIndex = new int[graph.nodesCount()];

    myHeadNodeIndex = headNodeIndex;
    myStartLayoutIndexForHead = new int[headNodeIndex.size()];
  }

  private void dfs(int nodeIndex) {
    myDfsUtil.nodeDfsIterator(nodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        boolean firstVisit = myLayoutIndex[currentNode] == 0;
        if (firstVisit) myLayoutIndex[currentNode] = currentLayoutIndex;

        int childWithoutLayoutIndex = -1;
        for (int childNodeIndex : getDownNodes(myGraph, currentNode)) {
          if (myLayoutIndex[childNodeIndex] == 0) {
            childWithoutLayoutIndex = childNodeIndex;
            break;
          }
        }

        if (childWithoutLayoutIndex == -1) {
          if (firstVisit) currentLayoutIndex++;

          return DfsUtil.NextNode.NODE_NOT_FOUND;
        }
        else {
          return childWithoutLayoutIndex;
        }
      }
    });
  }

  @NotNull
  private GraphLayoutImpl build() {
    for (int i = 0; i < myHeadNodeIndex.size(); i++) {
      int headNodeIndex = myHeadNodeIndex.get(i);
      myStartLayoutIndexForHead[i] = currentLayoutIndex;

      dfs(headNodeIndex);
    }

    return new GraphLayoutImpl(myLayoutIndex, myHeadNodeIndex, myStartLayoutIndexForHead);
  }
}
