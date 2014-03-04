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

package com.intellij.vcs.log.newgraph.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.newgraph.impl.DuplicateParentFixer.fixDuplicateParentCommits;

public class PermanentGraphBuilder {

  @NotNull
  public static Pair<PermanentGraphImpl, Map<Integer, GraphCommit>> build(@NotNull Flags simpleNodes, @NotNull List<? extends GraphCommit> commits) {
    commits = fixDuplicateParentCommits(commits);
    assert commits.size() == simpleNodes.size();

    int longEdgesCount = 0;
    int[] nodeToHashIndex = new int[commits.size()];

    for (int nodeIndex = 0; nodeIndex < commits.size(); nodeIndex++) {
      GraphCommit commit = commits.get(nodeIndex);
      nodeToHashIndex[nodeIndex] = commit.getIndex();

      int nextCommitHashIndex = nextCommitHashIndex(commits, nodeIndex);

      int[] parentHashIndices = commit.getParentIndices();
      if (parentHashIndices.length == 1 && parentHashIndices[0] == nextCommitHashIndex) {
        simpleNodes.set(nodeIndex, true);
      } else {
        longEdgesCount += parentHashIndices.length;
      }
    }

    PermanentGraphBuilder builder = new PermanentGraphBuilder(commits, simpleNodes, longEdgesCount, nodeToHashIndex);
    PermanentGraphImpl permanentGraph = builder.build();
    return new Pair<PermanentGraphImpl, Map<Integer, GraphCommit>>(permanentGraph, builder.commitsWithNotLoadParentMap);
  }

  private static int nextCommitHashIndex(List<? extends GraphCommit> commits, int nodeIndex) {
    int nextCommitHashIndex = -1;
    if (nodeIndex < commits.size() - 1)
      nextCommitHashIndex = commits.get(nodeIndex + 1).getIndex();
    return nextCommitHashIndex;
  }


  private final List<? extends GraphCommit> myCommits;
  private final Flags mySimpleNodes;

  private final int myNodesCount;

  private final int[] myNodeToHashIndex;

  private final int[] myNodeToEdgeIndex;
  private final int[] myLongEdges;

  // downHashIndex -> List of upNodeIndex
  private final Map<Integer, List<Integer>> upAdjacentNodes = new HashMap<Integer, List<Integer>>();

  // commitHash -> GraphCommit
  private final Map<Integer, GraphCommit> commitsWithNotLoadParentMap = new HashMap<Integer, GraphCommit>();

  private PermanentGraphBuilder(List<? extends GraphCommit> commits, Flags simpleNodes, int longEdgesCount, int[] nodeToHashIndex) {
    myCommits = commits;
    mySimpleNodes = simpleNodes;
    myNodeToHashIndex = nodeToHashIndex;

    myNodesCount = simpleNodes.size();

    myNodeToEdgeIndex = new int[myNodesCount + 1];
    myLongEdges = new int[2 * longEdgesCount];
  }

  private void addUnderdoneEdge(int upNodeIndex, int downHashIndex) {
    List<Integer> upNodes = upAdjacentNodes.get(downHashIndex);
    if (upNodes == null) {
      upNodes = new SmartList<Integer>();
      upAdjacentNodes.put(downHashIndex, upNodes);
    }
    upNodes.add(upNodeIndex);
  }

  private void addCommitWithNotLoadParent(int nodeIndex) {
    GraphCommit commit = myCommits.get(nodeIndex);
    commitsWithNotLoadParentMap.put(commit.getIndex(), commit);
  }

  private void fixUnderdoneEdgeForNotLoadCommit(int upNodeIndex) {
    for (int edgeIndex = myNodeToEdgeIndex[upNodeIndex]; edgeIndex < myNodeToEdgeIndex[upNodeIndex + 1]; edgeIndex++) {
      if (myLongEdges[edgeIndex] == -1) {
        addCommitWithNotLoadParent(upNodeIndex);
        myLongEdges[edgeIndex] = SomeGraph.NOT_LOAD_COMMIT;
        return;
      }
    }
    throw new IllegalStateException("Not found underdone edge to not load commit for node: " + upNodeIndex);
  }

  private void fixUnderdoneEdge(int upNodeIndex, int downNodeIndex, int downNodeHashIndex) {
    int end = myNodeToEdgeIndex[upNodeIndex + 1];

    GraphCommit upCommit = myCommits.get(upNodeIndex);
    int[] parentHashIndices = upCommit.getParentIndices();

    for (int i = 0; i < parentHashIndices.length; i++) {
      if (parentHashIndices[i] == downNodeHashIndex) {
        int offset = parentHashIndices.length - i;
        int edgeIndex = end - offset;

        if (myLongEdges[edgeIndex] == -1) {
          myLongEdges[edgeIndex] = downNodeIndex;
          return;
        } else {
          throw new IllegalStateException("Edge was set early!. Up node: " + upNodeIndex + ", down node: " + downNodeIndex);
        }
      }
    }
    throw new IllegalStateException("Not found underdone edges for node: " + upNodeIndex + ". Adjacent down node: " + downNodeIndex);
  }

  private void doStep(int nodeIndex) {
    GraphCommit commit = myCommits.get(nodeIndex);

    List<Integer> upNodes = upAdjacentNodes.remove(commit.getIndex());
    if (upNodes == null)
      upNodes = Collections.emptyList();

    int edgeIndex = myNodeToEdgeIndex[nodeIndex];
    for (Integer upNodeIndex : upNodes) {
      fixUnderdoneEdge(upNodeIndex, nodeIndex, commit.getIndex());
      myLongEdges[edgeIndex] = upNodeIndex;
      edgeIndex++;
    }

    // down nodes
    if (!mySimpleNodes.get(nodeIndex)) {
      for (Integer downHashIndex : commit.getParentIndices()) {
        addUnderdoneEdge(nodeIndex, downHashIndex);
        myLongEdges[edgeIndex] = -1;
        edgeIndex++;
      }
    }

    myNodeToEdgeIndex[nodeIndex + 1] = edgeIndex;
  }

  private void fixUnderdoneEdges() {
    for (List<Integer> upNodes : upAdjacentNodes.values()) {
      for (Integer upNodeIndex : upNodes)
        fixUnderdoneEdgeForNotLoadCommit(upNodeIndex);
    }
  }

  private PermanentGraphImpl build() {
    for (int nodeIndex = 0; nodeIndex < myNodesCount; nodeIndex++) {
      doStep(nodeIndex);
    }

    fixUnderdoneEdges();

    return new PermanentGraphImpl(mySimpleNodes, myNodeToHashIndex, myNodeToEdgeIndex, myLongEdges);
  }

}
