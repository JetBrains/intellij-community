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

import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.InvalidRequestException;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.facade.utils.Flags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.vcs.log.newgraph.utils.MyUtils.setAllValues;

public class CurrentBranches {
  private final PermanentGraph myPermanentGraph;
  private final Flags myVisibleNodesInBranches;
  private final DfsUtil myDfsUtil;

  public CurrentBranches(PermanentGraph permanentGraph, Flags visibleNodesInBranches, DfsUtil dfsUtil) {
    myPermanentGraph = permanentGraph;
    myVisibleNodesInBranches = visibleNodesInBranches;
    myDfsUtil = dfsUtil;
    setVisibleBranches(null);
  }

  public Flags getVisibleNodesInBranches() {
    return myVisibleNodesInBranches;
  }

  public void setVisibleBranches(@Nullable Set<Integer> heads) {
    if (heads == null) {
      setAllValues(myVisibleNodesInBranches, true);
      return;
    }
    Set<Integer> startedNodes = new HashSet<Integer>();
    for (int i = 0; i < myPermanentGraph.nodesCount(); i++) {
      if (heads.contains(myPermanentGraph.getHashIndex(i)))
        startedNodes.add(i);
    }
    if (startedNodes.size() != heads.size() || heads.isEmpty()) {
      throw new InvalidRequestException("Heads size is invalid! startedNodes " + startedNodes + "; heads: " + heads);
    }
    setAllValues(myVisibleNodesInBranches, false);
    selectAllVisibleNodes(startedNodes);
  }

  private void selectAllVisibleNodes(@NotNull Set<Integer> startedNodes) {
    for (int startNode : startedNodes) {
      myVisibleNodesInBranches.set(startNode, true);
      myDfsUtil.nodeDfsIterator(startNode, new DfsUtil.NextNode() {
        @Override
        public int fun(int currentNode) {
          for (int downNode : myPermanentGraph.getDownNodes(currentNode)) {
            if (downNode != SomeGraph.NOT_LOAD_COMMIT && !myVisibleNodesInBranches.get(downNode)) {
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
