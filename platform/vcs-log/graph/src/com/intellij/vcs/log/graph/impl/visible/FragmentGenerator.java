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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentGenerator {
  private static final int SHORT_FRAGMENT_MAX_SIZE = 10;
  private static final int MAX_SEARCH_SIZE = 10;

  @NotNull
  private final LinearGraph myLinearGraph;

  @NotNull
  private final Condition<Integer> myThisNodeCantBeInMiddle;

  private final Function<Integer, List<Integer>> upNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myLinearGraph.getUpNodes(integer);
    }
  };

  private final Function<Integer, List<Integer>> downNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myLinearGraph.getDownNodes(integer);
    }
  };

  public FragmentGenerator(@NotNull LinearGraph linearGraph, @NotNull Condition<Integer> thisNodeCantBeInMiddle) {
    myLinearGraph = linearGraph;
    myThisNodeCantBeInMiddle = thisNodeCantBeInMiddle;
  }

  @Nullable
  public GraphFragment getRelativeFragment(@NotNull GraphElement element) {
    int upNodeIndex;
    int downNodeIndex;
    if (element instanceof GraphNode) {
      upNodeIndex = ((GraphNode)element).getNodeIndex();
      downNodeIndex = upNodeIndex;
    } else {
      GraphEdge graphEdge = ((GraphEdge)element);
      upNodeIndex = graphEdge.getUpNodeIndex();
      downNodeIndex = graphEdge.getDownNodeIndex();
    }

    for (int i = 0; i < MAX_SEARCH_SIZE; i++) {
      GraphFragment graphFragment = getDownFragment(upNodeIndex);
      if (graphFragment != null && graphFragment.downNodeIndex >= downNodeIndex)
        return graphFragment;

      List<Integer> upNodes = myLinearGraph.getUpNodes(upNodeIndex);
      if (upNodes.size() != 1) {
        break;
      }
      upNodeIndex = upNodes.get(0);
    }

    return null;
  }

  @Nullable
  public GraphFragment getDownFragment(int upperVisibleNodeIndex) {
    Pair<Integer, Integer> fragment = getFragment(upperVisibleNodeIndex, downNodesFun, upNodesFun, myThisNodeCantBeInMiddle);
    return fragment == null ? null : new GraphFragment(fragment.first, fragment.second);
  }

  @Nullable
  public GraphFragment getUpFragment(int lowerNodeIndex) {
    Pair<Integer, Integer> fragment = getFragment(lowerNodeIndex, upNodesFun, downNodesFun, myThisNodeCantBeInMiddle);
    return fragment == null ? null : new GraphFragment(fragment.second, fragment.first);
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
    if (startFragment == null)
      return null;

    GraphFragment shortFragment;

    int maxDown = startFragment.downNodeIndex;
    while ((shortFragment = getDownFragment(maxDown)) != null && !myThisNodeCantBeInMiddle.value(maxDown)) {
      maxDown = shortFragment.downNodeIndex;
      if (maxDown - startFragment.downNodeIndex > bound)
        break;
    }

    int maxUp = startFragment.upNodeIndex;
    while ((shortFragment = getUpFragment(maxUp)) != null && !myThisNodeCantBeInMiddle.value(maxUp)) {
      maxUp = shortFragment.upNodeIndex;
      if (startFragment.upNodeIndex - maxUp > bound)
        break;
    }

    if (maxUp != startFragment.upNodeIndex || maxDown != startFragment.downNodeIndex) {
      return new GraphFragment(maxUp, maxDown);
    } else {
      if (myLinearGraph.getDownNodes(startFragment.upNodeIndex).size() != 1)
        return startFragment;
    }
    return null;
  }

  @Nullable
  private static Pair<Integer, Integer> getFragment(int startNode,
                                                    Function<Integer, List<Integer>> getNextNodes,
                                                    Function<Integer, List<Integer>> getPrevNodes,
                                                    Condition<Integer> thisNodeCantBeInMiddle) {
    Set<Integer> blackNodes = new HashSet<Integer>();
    blackNodes.add(startNode);

    Set<Integer> grayNodes = new HashSet<Integer>();
    grayNodes.addAll(getNextNodes.fun(startNode));

    int endNode = -1;
    while (blackNodes.size() < SHORT_FRAGMENT_MAX_SIZE && !grayNodes.contains(LinearGraph.NOT_LOAD_COMMIT)) {
      int nextBlackNode = -1;
      for (int grayNode : grayNodes) {
        if (blackNodes.containsAll(getPrevNodes.fun(grayNode))) {
          nextBlackNode = grayNode;
          break;
        }
      }

      if (nextBlackNode == -1)
        return null;

      if (grayNodes.size() == 1) {
        endNode = nextBlackNode;
        break;
      }

      List<Integer> nextGrayNodes = getNextNodes.fun(nextBlackNode);
      if (nextGrayNodes.isEmpty() || thisNodeCantBeInMiddle.value(nextBlackNode))
        return null;

      blackNodes.add(nextBlackNode);
      grayNodes.remove(nextBlackNode);
      grayNodes.addAll(nextGrayNodes);
    }

    if (endNode != -1)
      return Pair.create(startNode, endNode);
    else
      return null;
  }

  public static class GraphFragment {
    public final int upNodeIndex;
    public final int downNodeIndex;

    public GraphFragment(int upNodeIndex, int downNodeIndex) {
      this.upNodeIndex = upNodeIndex;
      this.downNodeIndex = downNodeIndex;
    }
  }
}
