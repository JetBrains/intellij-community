// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.Dfs;
import com.intellij.vcs.log.graph.utils.DfsUtilKt;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class BekBranchCreator {
  private final @NotNull LinearGraph myPermanentGraph;
  private final @NotNull GraphLayoutImpl myGraphLayout;
  private final @NotNull Flags myDoneNodes;

  private final @NotNull BekEdgeRestrictions myEdgeRestrictions = new BekEdgeRestrictions();

  private final @NotNull IntList myDownNodesBuffer = new IntArrayList();
  private final @NotNull IntList myUpNodesBuffer = new IntArrayList();

  BekBranchCreator(@NotNull LinearGraph permanentGraph, @NotNull GraphLayoutImpl graphLayout) {
    myPermanentGraph = permanentGraph;
    myGraphLayout = graphLayout;
    myDoneNodes = new BitSetFlags(permanentGraph.nodesCount(), false);
  }

  public @NotNull Pair<List<BekBranch>, BekEdgeRestrictions> getResult() {
    List<BekBranch> bekBranches = new ArrayList<>();

    for (int headNode : myGraphLayout.getHeadNodeIndex()) {
      if (myDoneNodes.get(headNode)) continue;
      IntList nextBranch = createNextBranch(headNode);
      bekBranches.add(new BekBranch(myPermanentGraph, nextBranch));
    }
    return Pair.create(bekBranches, myEdgeRestrictions);
  }

  public @NotNull IntList createNextBranch(int headNode) {
    myDoneNodes.set(headNode, true);
    IntList nodeIndexes = new IntArrayList();
    nodeIndexes.add(headNode);

    final int startLayout = myGraphLayout.getLayoutIndex(headNode);

    DfsUtilKt.walk(headNode, currentNode -> {
      int currentLayout = myGraphLayout.getLayoutIndex(currentNode);
      LinearGraphUtils.collectDownNodes(myPermanentGraph, currentNode, myDownNodesBuffer);
      for (int i = myDownNodesBuffer.size() - 1; i >= 0; i--) {
        int downNode = myDownNodesBuffer.getInt(i);

        if (myDoneNodes.get(downNode)) {
          if (myGraphLayout.getLayoutIndex(downNode) < startLayout) myEdgeRestrictions.addRestriction(currentNode, downNode);
        }
        else if (currentLayout <= myGraphLayout.getLayoutIndex(downNode)) {

          // almost ok node, except (may be) up nodes
          boolean hasUndoneUpNodes = false;
          LinearGraphUtils.collectUpNodes(myPermanentGraph, downNode, myUpNodesBuffer);
          for (int j = 0; j < myUpNodesBuffer.size(); j++) {
            int upNode = myUpNodesBuffer.getInt(j);
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
