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
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

class BekBranchCreator {
  @NotNull private final LinearGraph myPermanentGraph;
  @NotNull private final GraphLayoutImpl myGraphLayout;
  @NotNull private final Flags myDoneNodes;

  @NotNull private final DfsUtil myDfsUtil = new DfsUtil();
  @NotNull private final BekEdgeRestrictions myEdgeRestrictions = new BekEdgeRestrictions();

  public BekBranchCreator(@NotNull LinearGraph permanentGraph, @NotNull GraphLayoutImpl graphLayout) {
    myPermanentGraph = permanentGraph;
    myGraphLayout = graphLayout;
    myDoneNodes = new BitSetFlags(permanentGraph.nodesCount(), false);
  }

  @NotNull
  public Pair<List<BekBranch>, BekEdgeRestrictions> getResult() {
    List<BekBranch> bekBranches = ContainerUtil.newArrayList();

    for (int headNode : myGraphLayout.getHeadNodeIndex()) {
      List<Integer> nextBranch = createNextBranch(headNode);
      bekBranches.add(new BekBranch(myPermanentGraph, nextBranch));
    }
    return Pair.create(bekBranches, myEdgeRestrictions);
  }

  public List<Integer> createNextBranch(int headNode) {
    final List<Integer> nodeIndexes = ContainerUtil.newArrayList();

    assert !myDoneNodes.get(headNode);
    myDoneNodes.set(headNode, true);
    nodeIndexes.add(headNode);

    final int startLayout = myGraphLayout.getLayoutIndex(headNode);

    myDfsUtil.nodeDfsIterator(headNode, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
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
        return NODE_NOT_FOUND;
      }
    });

    return nodeIndexes;
  }

}
