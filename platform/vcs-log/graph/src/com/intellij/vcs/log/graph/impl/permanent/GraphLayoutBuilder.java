// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.Dfs;
import com.intellij.vcs.log.graph.utils.DfsUtilKt;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

public final class GraphLayoutBuilder {

  private static final Logger LOG = Logger.getInstance(GraphLayoutBuilder.class);

  @NotNull
  public static GraphLayoutImpl build(@NotNull LinearGraph graph, @NotNull IntComparator headNodeIndexComparator) {
    IntList heads = new IntArrayList();
    for (int i = 0; i < graph.nodesCount(); i++) {
      if (getUpNodes(graph, i).isEmpty()) {
        heads.add(i);
      }
    }
    try {
      heads.sort(headNodeIndexComparator);
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
  private final int @NotNull [] myLayoutIndex;

  @NotNull private final IntList myHeadNodeIndex;
  private final int @NotNull [] myStartLayoutIndexForHead;

  private int currentLayoutIndex = 1;

  private GraphLayoutBuilder(@NotNull LinearGraph graph, @NotNull IntList headNodeIndex) {
    myGraph = graph;
    myLayoutIndex = new int[graph.nodesCount()];

    myHeadNodeIndex = headNodeIndex;
    myStartLayoutIndexForHead = new int[headNodeIndex.size()];
  }

  private void dfs(int nodeIndex) {
    DfsUtilKt.walk(nodeIndex, currentNode -> {
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

        return Dfs.NextNode.NODE_NOT_FOUND;
      }
      else {
        return childWithoutLayoutIndex;
      }
    });
  }

  @NotNull
  private GraphLayoutImpl build() {
    for (int i = 0; i < myHeadNodeIndex.size(); i++) {
      int headNodeIndex = myHeadNodeIndex.getInt(i);
      myStartLayoutIndexForHead[i] = currentLayoutIndex;

      dfs(headNodeIndex);
    }

    return new GraphLayoutImpl(myLayoutIndex, myHeadNodeIndex, myStartLayoutIndexForHead);
  }
}
