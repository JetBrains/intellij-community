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

package com.intellij.vcs.log.newgraph.gpaph.fragments;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.impl.CollapsedMutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentGenerator {
  private static final int SHORT_FRAGMENT_MAX_SIZE = 100;
  private static final int MAX_SEARCH_SIZE = 10;

  @NotNull
  private final CollapsedMutableGraph myMutableGraph;

  @NotNull
  private final Set<Integer> myBranchNodeIndexes;

  private final Function<Integer, List<Integer>> upNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myMutableGraph.getInternalGraph().getUpNodes(integer);
    }
  };

  private final Function<Integer, List<Integer>> downNodesFun = new Function<Integer, List<Integer>>() {
    @Override
    public List<Integer> fun(Integer integer) {
      return myMutableGraph.getInternalGraph().getDownNodes(integer);
    }
  };

  private final Function<Integer, Boolean> thisNodeCantBeInMiddle = new Function<Integer, Boolean>() {
    @Override
    public Boolean fun(Integer integer) {
      return myBranchNodeIndexes.contains(integer);
    }
  };


  public FragmentGenerator(@NotNull CollapsedMutableGraph mutableGraph, @NotNull Set<Integer> branchNodeIndexes) {
    myMutableGraph = mutableGraph;
    myBranchNodeIndexes = branchNodeIndexes;
  }

  @Nullable
  public GraphFragment getRelativeFragment(@NotNull GraphElement element) {
    Node upNode;
    int downVisibleIndex;
    if (element instanceof Node) {
      upNode = (Node) element;
      downVisibleIndex = upNode.getVisibleNodeIndex();
    } else {
      Edge edge = (Edge)element;
      upNode = myMutableGraph.getNode(edge.getUpNodeVisibleIndex());
      downVisibleIndex = edge.getDownNodeVisibleIndex();
    }

    for (int i = 0; i < MAX_SEARCH_SIZE; i++) {
      GraphFragment graphFragment = getDownFragment(upNode.getVisibleNodeIndex());
      if (graphFragment != null && graphFragment.downVisibleNodeIndex >= downVisibleIndex)
        return graphFragment;

      if (upNode.getUpEdges().size() != 1) {
        break;
      }
      upNode = myMutableGraph.getNode(upNode.getUpEdges().get(0).getUpNodeVisibleIndex());
    }

    return null;
  }

  @Nullable
  public GraphFragment getDownFragment(int upperVisibleNodeIndex) {
    Pair<Integer, Integer> fragment = getFragment(myMutableGraph.getIndexInPermanentGraph(upperVisibleNodeIndex),
                                                  downNodesFun,
                                                  upNodesFun,
                                                  thisNodeCantBeInMiddle);
    return fragment == null ? null : new GraphFragment(myMutableGraph.toVisibleIndex(fragment.first), myMutableGraph.toVisibleIndex(fragment.second));
  }

  @Nullable
  public GraphFragment getUpFragment(int lowerNodeIndex) {
    Pair<Integer, Integer> fragment = getFragment(myMutableGraph.getIndexInPermanentGraph(lowerNodeIndex),
                                                  upNodesFun,
                                                  downNodesFun,
                                                  thisNodeCantBeInMiddle);
    return fragment == null ? null : new GraphFragment(myMutableGraph.toVisibleIndex(fragment.second), myMutableGraph.toVisibleIndex(fragment.first));
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

    int maxDown = startFragment.downVisibleNodeIndex;
    while ((shortFragment = getDownFragment(maxDown)) != null && !myBranchNodeIndexes.contains(myMutableGraph.getIndexInPermanentGraph(maxDown))) {
      maxDown = shortFragment.downVisibleNodeIndex;
      if (maxDown - startFragment.downVisibleNodeIndex > bound)
        break;
    }

    int maxUp = startFragment.upVisibleNodeIndex;
    while ((shortFragment = getUpFragment(maxUp)) != null && !myBranchNodeIndexes.contains(myMutableGraph.getIndexInPermanentGraph(maxUp))) {
      maxUp = shortFragment.upVisibleNodeIndex;
      if (startFragment.upVisibleNodeIndex - maxUp > bound)
        break;
    }

    if (maxUp != startFragment.upVisibleNodeIndex || maxDown != startFragment.downVisibleNodeIndex) {
      return new GraphFragment(maxUp, maxDown);
    } else {
      if (myMutableGraph.getNode(startFragment.upVisibleNodeIndex).getDownEdges().size() != 1)
        return startFragment;
    }
    return null;
  }

  @Nullable
  private static Pair<Integer, Integer> getFragment(int startNode,
                                                    Function<Integer, List<Integer>> getNextNodes,
                                                    Function<Integer, List<Integer>> getPrevNodes,
                                                    Function<Integer, Boolean> thisNodeCantBeInMiddle) {
    Set<Integer> blackNodes = new HashSet<Integer>();
    blackNodes.add(startNode);

    Set<Integer> grayNodes = new HashSet<Integer>();
    grayNodes.addAll(getNextNodes.fun(startNode));

    int endNode = -1;
    while (blackNodes.size() < SHORT_FRAGMENT_MAX_SIZE && !grayNodes.contains(SomeGraph.NOT_LOAD_COMMIT)) {
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
      if (nextGrayNodes.isEmpty() || thisNodeCantBeInMiddle.fun(nextBlackNode))
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
}
