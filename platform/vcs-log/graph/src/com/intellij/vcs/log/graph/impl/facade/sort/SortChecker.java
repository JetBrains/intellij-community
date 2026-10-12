// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

public final class SortChecker {
  private static final Logger LOG = Logger.getInstance(SortChecker.class);

  /**
   * Checks that {@code sortIndexMap} keeps each edge of {@code permanentGraph} pointing downwards.
   */
  public static void checkSortIndexMap(@NotNull LinearGraph permanentGraph, @NotNull SortIndexMap sortIndexMap) {
    Pair<Integer, Integer> reversedEdge = findReversedEdge(permanentGraph, sortIndexMap);
    if (reversedEdge != null) {
      LOG.error("Illegal edge: up node " + reversedEdge.first + ", downNode " + reversedEdge.second);
    }
  }

  public static @Nullable Pair<Integer, Integer> findReversedEdge(@NotNull LinearGraph linearGraph) {
    for (int i = 0; i < linearGraph.nodesCount(); i++) {
      for (int downNode : getDownNodes(linearGraph, i)) {
        if (downNode <= i) {
          return Pair.create(i, downNode);
        }
      }
      for (int upNode : getUpNodes(linearGraph, i)) {
        if (upNode >= i) {
          return Pair.create(upNode, i);
        }
      }
    }
    return null;
  }

  /**
   * @return the sorted indexes of the up node and the down node of the first edge that does not point downwards, or null
   */
  public static @Nullable Pair<Integer, Integer> findReversedEdge(@NotNull LinearGraph permanentGraph, @NotNull SortIndexMap sortIndexMap) {
    IntList downNodes = new IntArrayList();
    for (int i = 0; i < permanentGraph.nodesCount(); i++) {
      int sortedUpNode = sortIndexMap.getSortedIndex(i);
      LinearGraphUtils.collectDownNodes(permanentGraph, i, downNodes);
      for (int j = 0; j < downNodes.size(); j++) {
        int sortedDownNode = sortIndexMap.getSortedIndex(downNodes.getInt(j));
        if (sortedDownNode <= sortedUpNode) {
          return Pair.create(sortedUpNode, sortedDownNode);
        }
      }
    }
    return null;
  }
}
