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

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CurrentBranches {

  @NotNull
  public static Flags getVisibleNodes(@NotNull LinearGraph permanentGraph, @NotNull Set<Integer> headNodeIndexes) {
    assert !headNodeIndexes.isEmpty();
    CurrentBranches currentBranches = new CurrentBranches(permanentGraph);
    currentBranches.selectAllVisibleNodes(headNodeIndexes);
    return currentBranches.myVisibleNodesInBranches;
  }

  @NotNull
  private final LinearGraph myPermanentGraph;


  @NotNull
  private final Flags myVisibleNodesInBranches;

  @NotNull
  private final DfsUtil myDfsUtil = new DfsUtil();

  public CurrentBranches(@NotNull LinearGraph permanentGraph) {
    myPermanentGraph = permanentGraph;
    myVisibleNodesInBranches = new BitSetFlags(permanentGraph.nodesCount(), false);
  }

  private void selectAllVisibleNodes(@NotNull Set<Integer> startedNodes) {
    for (int startNode : startedNodes) {
      myVisibleNodesInBranches.set(startNode, true);
      myDfsUtil.nodeDfsIterator(startNode, new DfsUtil.NextNode() {
        @Override
        public int fun(int currentNode) {
          for (int downNode : myPermanentGraph.getDownNodes(currentNode)) {
            if (downNode != LinearGraph.NOT_LOAD_COMMIT && !myVisibleNodesInBranches.get(downNode)) {
              myVisibleNodesInBranches.set(downNode, true);
              return downNode;
            }
          }
          return NODE_NOT_FOUND;
        }
      });
    }
  }
}
