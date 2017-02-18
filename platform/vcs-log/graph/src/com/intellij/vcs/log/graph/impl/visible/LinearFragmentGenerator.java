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

import com.intellij.util.Function;
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

import static com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter.DOWN;
import static com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter.UP;

public class LinearFragmentGenerator {
  private static final int SHORT_FRAGMENT_MAX_SIZE = 10;
  private static final int MAX_SEARCH_SIZE = 10;

  @NotNull private final LiteLinearGraph myLinearGraph;

  @NotNull private final Set<Integer> myPinnedNodes;

  private final Function<Integer, List<Integer>> upNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myLinearGraph.getNodes(integer, UP);
    }
  };

  private final Function<Integer, List<Integer>> downNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myLinearGraph.getNodes(integer, DOWN);
    }
  };

  public LinearFragmentGenerator(@NotNull LiteLinearGraph linearGraph, @NotNull Set<Integer> pinnedNodes) {
    myLinearGraph = linearGraph;
    myPinnedNodes = pinnedNodes;
  }

  @Nullable
  public GraphFragment getRelativeFragment(@NotNull GraphElement element) {
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

  @Nullable
  public GraphFragment getDownFragment(int upperVisibleNodeIndex) {
    return getFragment(upperVisibleNodeIndex, downNodesFun, upNodesFun, myPinnedNodes, true);
  }

  @Nullable
  public GraphFragment getUpFragment(int lowerNodeIndex) {
    return getFragment(lowerNodeIndex, upNodesFun, downNodesFun, myPinnedNodes, false);
  }

  @Nullable
  public GraphFragment getLongDownFragment(int rowIndex) {
    return getLongFragment(getDownFragment(rowIndex), Integer.MAX_VALUE);
  }

  @Nullable
  public GraphFragment getLongFragment(@NotNull GraphElement element) {
    return getLongFragment(getRelativeFragment(element), Integer.MAX_VALUE);
  }

  // for hover
  @Nullable
  public GraphFragment getPartLongFragment(@NotNull GraphElement element) {
    return getLongFragment(getRelativeFragment(element), 500);
  }

  @Nullable
  private GraphFragment getLongFragment(@Nullable GraphFragment startFragment, int bound) {
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

  @Nullable
  private static GraphFragment getFragment(int startNode,
                                           Function<Integer, List<Integer>> getNextNodes,
                                           Function<Integer, List<Integer>> getPrevNodes,
                                           Set<Integer> thisNodeCantBeInMiddle, boolean isDown) {
    Set<Integer> blackNodes = new HashSet<>();
    blackNodes.add(startNode);

    Set<Integer> grayNodes = new HashSet<>();
    grayNodes.addAll(getNextNodes.fun(startNode));

    int endNode = -1;
    while (blackNodes.size() < SHORT_FRAGMENT_MAX_SIZE) {
      int nextBlackNode = -1;
      for (int grayNode : grayNodes) {
        if (blackNodes.containsAll(getPrevNodes.fun(grayNode))) {
          nextBlackNode = grayNode;
          break;
        }
      }

      if (nextBlackNode == -1) return null;

      if (grayNodes.size() == 1) {
        endNode = nextBlackNode;
        break;
      }

      List<Integer> nextGrayNodes = getNextNodes.fun(nextBlackNode);
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
