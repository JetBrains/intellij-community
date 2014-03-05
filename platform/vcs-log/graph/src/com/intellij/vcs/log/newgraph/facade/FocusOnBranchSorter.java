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

package com.intellij.vcs.log.newgraph.facade;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphBuilder;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphImpl;
import com.intellij.vcs.log.newgraph.impl.PermanentGraphLayoutBuilder;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.newgraph.utils.Flags;
import com.intellij.vcs.log.newgraph.utils.MyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FocusOnBranchSorter {

  @NotNull
  public static List<GraphCommit> sortCommits(@NotNull List<? extends GraphCommit> commits,
                                              @NotNull final GraphColorManager colorManager) {
    long ms = System.currentTimeMillis();

    GraphFlags flags = new GraphFlags(commits.size());
    Pair<PermanentGraphImpl,Map<Integer,GraphCommit>>
      graphAndUnderdoneCommits = PermanentGraphBuilder.build(flags.getSimpleNodeFlags(), commits);
    final PermanentGraphImpl permanentGraph = graphAndUnderdoneCommits.first;

    DfsUtil dfsUtil = new DfsUtil(commits.size());

    final PermanentGraphLayout graphLayout = PermanentGraphLayoutBuilder.build(dfsUtil, permanentGraph, new Comparator<Integer>() {
      @Override
      public int compare(@NotNull Integer o1, @NotNull Integer o2) {
        int hashIndex1 = permanentGraph.getHashIndex(o1);
        int hashIndex2 = permanentGraph.getHashIndex(o2);
        return colorManager.compareHeads(hashIndex2, hashIndex1);
      }
    });
    List<Integer> result = new FocusOnBranchSorter(permanentGraph, graphLayout, dfsUtil, flags.getTempFlags()).getResult();

    System.out.println(System.currentTimeMillis() - ms);
    return new GraphCommits(permanentGraph, result);
  }

  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final PermanentGraphLayout myGraphLayout;

  @NotNull
  private final List<Integer> myResultList;

  @NotNull
  private final DfsUtil myDfsUtil;

  @NotNull
  private final Flags myDoneNodes;

  public FocusOnBranchSorter(@NotNull PermanentGraph permanentGraph,
                             @NotNull PermanentGraphLayout graphLayout,
                             @NotNull DfsUtil dfsUtil,
                             @NotNull Flags doneNodes) {
    myPermanentGraph = permanentGraph;
    myGraphLayout = graphLayout;
    myDfsUtil = dfsUtil;
    myDoneNodes = doneNodes;
    myResultList = new ArrayList<Integer>(permanentGraph.nodesCount());
  }

  @NotNull
  public List<Integer> getResult() {
    MyUtils.setAllValues(myDoneNodes, false);
    List<Integer> sortedNodes = myGraphLayout.getStartedSortedNodes();
    layBranch(sortedNodes.get(0), myResultList);
    for (int i = 1; i < sortedNodes.size(); i++) {
      List<Integer> appendix = new ArrayList<Integer>();
      Set<Integer> oldAdjacentNodes = layBranch(sortedNodes.get(i), appendix);
      insertAppendix(appendix, oldAdjacentNodes);
    }
    return myResultList;
  }

  private void insertAppendix(@NotNull List<Integer> appendix, @NotNull Set<Integer> allOldAdjacentNodes) {
    Pair<Integer, Set<Integer>> lastPartAndAdjacentNodes = getLastPartAndAdjacentNodes(appendix, allOldAdjacentNodes);
    List<Integer> appendixPart = appendix.subList(lastPartAndAdjacentNodes.first, appendix.size());

    int prevInsertIndex = getInsertIndex(appendixPart, lastPartAndAdjacentNodes.second);
    myResultList.addAll(prevInsertIndex, appendixPart);

    while (lastPartAndAdjacentNodes.first > 0) {
      allOldAdjacentNodes.addAll(appendixPart);
      appendixPart.clear();
      lastPartAndAdjacentNodes = getLastPartAndAdjacentNodes(appendix, allOldAdjacentNodes);
      appendixPart = appendix.subList(lastPartAndAdjacentNodes.first, appendix.size());

      int insertIndex = getInsertIndex(appendixPart, lastPartAndAdjacentNodes.second);
      myResultList.addAll(insertIndex, appendixPart);
    }
  }

  @NotNull
  private Pair<Integer, Set<Integer>> getLastPartAndAdjacentNodes(@NotNull List<Integer> appendix, @NotNull Set<Integer> allOldAdjacentNodes) {
    Set<Integer> adjacentNodes = new HashSet<Integer>();
    int startIndex = appendix.size() - 1;
    assertAllDownNodes(appendix.get(startIndex), allOldAdjacentNodes, adjacentNodes);

    while (startIndex >= 1) {
      if (Math.abs(appendix.get(startIndex) - appendix.get(startIndex - 1)) > 10)
        break;
      if (startIndex < appendix.size() - 50)
        break;

      startIndex--;
      assertAllDownNodes(appendix.get(startIndex), allOldAdjacentNodes, adjacentNodes);
    }
    return Pair.create(startIndex, adjacentNodes);
  }

  private void assertAllDownNodes(int nodeIndex, Set<Integer> allOldAdjacentNodes, Set<Integer> adjacentNodes) {
    for (int downNode : myPermanentGraph.getDownNodes(nodeIndex)) {
      if (allOldAdjacentNodes.contains(downNode))
        adjacentNodes.add(downNode);
    }
  }

  private int getInsertIndex(@NotNull List<Integer> appendixPart, @NotNull Set<Integer> oldAdjacentNodesForPart) {
    assert !appendixPart.isEmpty();
    int appendixNode= appendixPart.get(0);
    int insertIndex;
    if (oldAdjacentNodesForPart.isEmpty()) {
      insertIndex = myResultList.size() - 1;
    } else {
      insertIndex = myResultList.indexOf(Collections.min(oldAdjacentNodesForPart));
      for (int i = insertIndex - 1; i >= 0; i--) {
        if (oldAdjacentNodesForPart.contains(myResultList.get(i)))
          insertIndex = i;
      }
    }

    while (insertIndex >= 1) {
      if (myResultList.get(insertIndex - 1) < appendixNode)
        break;
      insertIndex--;
    }

    return insertIndex;
  }

  @NotNull
  private Set<Integer> layBranch(int startNode, @NotNull final List<Integer> resultList) {
    final Set<Integer> oldAdjacentNodes = new HashSet<Integer>();
    assert !myDoneNodes.get(startNode);

    final int startLayoutIndex = myGraphLayout.getLayoutIndex(startNode);
    resultList.add(startNode);
    myDoneNodes.set(startNode, true);
    myDfsUtil.nodeDfsIterator(startNode, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        int currentLayout = myGraphLayout.getLayoutIndex(currentNode);
        List<Integer> downNodes = myPermanentGraph.getDownNodes(currentNode);
        for (int i = downNodes.size() - 1; i >= 0; i--) {
          int downNode = downNodes.get(i);
          if (downNode == SomeGraph.NOT_LOAD_COMMIT)
            return NODE_NOT_FOUND;

          if (myGraphLayout.getLayoutIndex(downNode) < startLayoutIndex)
            oldAdjacentNodes.add(downNode);

          if (!myDoneNodes.get(downNode) && myGraphLayout.getLayoutIndex(downNode) >= currentLayout) {
            myDoneNodes.set(downNode, true);
            resultList.add(downNode);
            return downNode;
          }
        }
        return NODE_NOT_FOUND;
      }
    });
    return oldAdjacentNodes;
  }

  private static class GraphCommits extends AbstractList<GraphCommit> {
    @NotNull
    private final PermanentGraph myPermanentGraph;

    @NotNull
    private final List<Integer> myNodeList;

    public GraphCommits(@NotNull PermanentGraph permanentGraph, @NotNull List<Integer> nodeList) {
      myPermanentGraph = permanentGraph;
      myNodeList = nodeList;
    }

    @Override
    public GraphCommit get(int index) {
      final int nodeIndex = myNodeList.get(index);
      List<Integer> downNodes = myPermanentGraph.getDownNodes(nodeIndex);
      final int[] parentHashes = new int[downNodes.size()];
      for (int i = 0; i < parentHashes.length; i++) {
        int downNode = downNodes.get(i);
        if (downNode == SomeGraph.NOT_LOAD_COMMIT) {
          parentHashes[i] = 0;
        } else {
          parentHashes[i] = myPermanentGraph.getHashIndex(downNode);
        }
      }
      return new GraphCommit() {
        @Override
        public int getIndex() {
          return myPermanentGraph.getHashIndex(nodeIndex);
        }

        @Override
        public int[] getParentIndices() {
          return parentHashes;
        }
      };
    }

    @Override
    public int size() {
      return myNodeList.size();
    }
  }
}
