// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.Dfs;
import com.intellij.vcs.log.graph.utils.DfsUtilKt;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

class BekBranchCreator {
  private final @NotNull LinearGraph myPermanentGraph;
  private final @NotNull GraphLayoutImpl myGraphLayout;
  private final @NotNull Flags myDoneNodes;

  private final @NotNull BekEdgeRestrictions myEdgeRestrictions = new BekEdgeRestrictions();

  BekBranchCreator(@NotNull LinearGraph permanentGraph, @NotNull GraphLayoutImpl graphLayout) {
    myPermanentGraph = permanentGraph;
    myGraphLayout = graphLayout;
    myDoneNodes = new BitSetFlags(permanentGraph.nodesCount(), false);
  }

  public @NotNull Pair<List<BekBranch>, BekEdgeRestrictions> getResult() {
    List<BekBranch> bekBranches = new ArrayList<>();

    for (int headNode : myGraphLayout.getHeadNodeIndex()) {
      if (myDoneNodes.get(headNode)) continue;
      List<Integer> nextBranch = createNextBranch(headNode);
      bekBranches.add(new BekBranch(myPermanentGraph, nextBranch));
    }
    return Pair.create(bekBranches, myEdgeRestrictions);
  }

  public List<Integer> createNextBranch(int headNode) {
    myDoneNodes.set(headNode, true);
    List<Integer> nodeIndexes = new ArrayList<>();
    nodeIndexes.add(headNode);

    final int startLayout = myGraphLayout.getLayoutIndex(headNode);

    DfsUtilKt.walk(headNode, currentNode -> {
      int currentLayout = myGraphLayout.getLayoutIndex(currentNode);
      List<Integer> downNodes = getDownNodes(myPermanentGraph, currentNode);
      for (int i = downNodes.size() - 1; i >= 0; i--) {
        int downNode = downNodes.get(i);

        if (myDoneNodes.get(downNode)) {
          if (myGraphLayout.getLayoutIndex(downNode) < startLayout) myEdgeRestrictions.addRestriction(currentNode, downNode);
        }
        else if (currentLayout <= myGraphLayout.getLayoutIndex(downNode)) {

          // almost ok node, except (may be) up nodes
          boolean hasUndoneUpNodes = false;
          for (int upNode : getUpNodes(myPermanentGraph, downNode)) {
            if (!myDoneNodes.get(upNode) && myGraphLayout.getLayoutIndex(upNode) <= myGraphLayout.getLayoutIndex(downNode)) {
              hasUndoneUpNodes = true;
              break;
            }
          }

          if (!hasUndoneUpNodes) {
            myDoneNodes.set(downNode, true);
            nodeIndexes.add(downNode);
            return downNode;
          }
        }
      }
      return Dfs.NextNode.NODE_NOT_FOUND;
    });

    return nodeIndexes;
  }
}
