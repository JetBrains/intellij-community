// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.collapsing;

import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.NormalEdge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter.DOWN;
import static com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter.UP;

public class LinearFragmentGenerator {
  private static final int SHORT_FRAGMENT_MAX_SIZE = 10;
  private static final int MAX_SEARCH_SIZE = 10;

  private final @NotNull LiteLinearGraph myLinearGraph;

  private final @NotNull Set<Integer> myPinnedNodes;

  private final Function<Integer, List<Integer>> upNodesFun = new Function<>() {
    @Override
    public List<Integer> apply(Integer integer) {
      return myLinearGraph.getNodes(integer, UP);
    }
  };

  private final Function<Integer, List<Integer>> downNodesFun = new Function<>() {
    @Override
    public List<Integer> apply(Integer integer) {
      return myLinearGraph.getNodes(integer, DOWN);
    }
  };

  public LinearFragmentGenerator(@NotNull LiteLinearGraph linearGraph, @NotNull Set<Integer> pinnedNodes) {
    myLinearGraph = linearGraph;
    myPinnedNodes = pinnedNodes;
  }

  public @Nullable GraphFragment getRelativeFragment(@NotNull GraphElement element) {
    int upNodeIndex;
    int downNodeIndex;
    if (element instanceof GraphNode) {
      upNodeIndex = ((GraphNode)element).getNodeIndex();
      downNodeIndex = upNodeIndex;
    }
    else {
      NormalEdge graphEdge = LinearGraphUtils.asNormalEdge(((GraphEdge)element));
      if (graphEdge == null) return null;

      upNodeIndex = graphEdge.up;
      downNodeIndex = graphEdge.down;
    }

    for (int i = 0; i < MAX_SEARCH_SIZE; i++) {
      GraphFragment graphFragment = getDownFragment(upNodeIndex);
      if (graphFragment != null && graphFragment.downNodeIndex >= downNodeIndex) return graphFragment;

      List<Integer> upNodes = myLinearGraph.getNodes(upNodeIndex, UP);
      if (upNodes.size() != 1) {
        break;
      }
      upNodeIndex = upNodes.get(0);
    }

    return null;
  }

  public @Nullable GraphFragment getDownFragment(int upperVisibleNodeIndex) {
    return getFragment(upperVisibleNodeIndex, downNodesFun, upNodesFun, myPinnedNodes, true);
  }

  public @Nullable GraphFragment getUpFragment(int lowerNodeIndex) {
    return getFragment(lowerNodeIndex, upNodesFun, downNodesFun, myPinnedNodes, false);
  }

  public @Nullable GraphFragment getLongDownFragment(int rowIndex) {
    return getLongFragment(getDownFragment(rowIndex), Integer.MAX_VALUE);
  }

  public @Nullable GraphFragment getLongFragment(@NotNull GraphElement element) {
    return getLongFragment(getRelativeFragment(element), Integer.MAX_VALUE);
  }

  // for hover
  public @Nullable GraphFragment getPartLongFragment(@NotNull GraphElement element) {
    return getLongFragment(getRelativeFragment(element), 500);
  }

  private @Nullable GraphFragment getLongFragment(@Nullable GraphFragment startFragment, int bound) {
    if (startFragment == null) return null;

    GraphFragment shortFragment;

    int maxDown = startFragment.downNodeIndex;
    while ((shortFragment = getDownFragment(maxDown)) != null && !myPinnedNodes.contains(maxDown)) {
      maxDown = shortFragment.downNodeIndex;
      if (maxDown - startFragment.downNodeIndex > bound) break;
    }

    int maxUp = startFragment.upNodeIndex;
    while ((shortFragment = getUpFragment(maxUp)) != null && !myPinnedNodes.contains(maxUp)) {
      maxUp = shortFragment.upNodeIndex;
      if (startFragment.upNodeIndex - maxUp > bound) break;
    }

    if (maxUp != startFragment.upNodeIndex || maxDown != startFragment.downNodeIndex) {
      return new GraphFragment(maxUp, maxDown);
    }
    else {
      // start fragment is Simple
      if (myLinearGraph.getNodes(startFragment.upNodeIndex, DOWN).size() != 1) return startFragment;
    }
    return null;
  }

  private static @Nullable GraphFragment getFragment(int startNode,
                                                     Function<? super Integer, ? extends List<Integer>> getNextNodes,
                                                     Function<? super Integer, ? extends List<Integer>> getPrevNodes,
                                                     Set<Integer> thisNodeCantBeInMiddle, boolean isDown) {
    Set<Integer> blackNodes = new HashSet<>();
    blackNodes.add(startNode);

    Set<Integer> grayNodes = new HashSet<>(getNextNodes.apply(startNode));

    int endNode = -1;
    while (blackNodes.size() < SHORT_FRAGMENT_MAX_SIZE) {
      int nextBlackNode = -1;
      for (int grayNode : grayNodes) {
        if (blackNodes.containsAll(getPrevNodes.apply(grayNode))) {
          nextBlackNode = grayNode;
          break;
        }
      }

      if (nextBlackNode == -1) return null;

      if (grayNodes.size() == 1) {
        endNode = nextBlackNode;
        break;
      }

      List<Integer> nextGrayNodes = getNextNodes.apply(nextBlackNode);
      if (nextGrayNodes.isEmpty() || thisNodeCantBeInMiddle.contains(nextBlackNode)) return null;

      blackNodes.add(nextBlackNode);
      grayNodes.remove(nextBlackNode);
      grayNodes.addAll(nextGrayNodes);
    }

    if (endNode != -1) {
      return isDown ? GraphFragment.create(startNode, endNode) : GraphFragment.create(endNode, startNode);
    }
    else {
      return null;
    }
  }

  public static class GraphFragment {
    public final int upNodeIndex;
    public final int downNodeIndex;

    public GraphFragment(int upNodeIndex, int downNodeIndex) {
      this.upNodeIndex = upNodeIndex;
      this.downNodeIndex = downNodeIndex;
    }

    public static GraphFragment create(int startNode, int endNode) {
      return new GraphFragment(startNode, endNode);
    }
  }
}
