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

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.impl.permanent.DuplicateParentFixer.fixDuplicateParentCommits;

public class PermanentLinearGraphBuilder<CommitId> {

  @NotNull
  public static <CommitId> PermanentLinearGraphBuilder<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits) {
    graphCommits = fixDuplicateParentCommits(graphCommits);
    Flags simpleNodes = new BitSetFlags(graphCommits.size());

    int longEdgesCount = 0;

    for (int nodeIndex = 0; nodeIndex < graphCommits.size(); nodeIndex++) {
      GraphCommit<CommitId> commit = graphCommits.get(nodeIndex);

      CommitId nextCommitHashIndex = nextCommitHashIndex(graphCommits, nodeIndex);

      List parents = commit.getParents();
      if (parents.size() == 1 && parents.get(0).equals(nextCommitHashIndex)) {
        simpleNodes.set(nodeIndex, true);
      } else {
        longEdgesCount += parents.size();
      }
    }

    return new PermanentLinearGraphBuilder<CommitId>(graphCommits, simpleNodes, longEdgesCount);
  }

  @Nullable
  private static <CommitId> CommitId nextCommitHashIndex(List<? extends GraphCommit<CommitId>> commits, int nodeIndex) {
    if (nodeIndex < commits.size() - 1)
      return commits.get(nodeIndex + 1).getId();
    return null;
  }

  private final List<? extends GraphCommit<CommitId>> myCommits;
  private final Flags mySimpleNodes;

  private final int myNodesCount;

  private final int[] myNodeToEdgeIndex;
  private final int[] myLongEdges;

  // downCommitId -> List of upNodeIndex
  private final Map<CommitId, List<Integer>> upAdjacentNodes = new HashMap<CommitId, List<Integer>>();

  // commitHash -> GraphCommit
  private final Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent = new HashMap<CommitId, GraphCommit<CommitId>>();

  private PermanentLinearGraphBuilder(List<? extends GraphCommit<CommitId>> commits, Flags simpleNodes, int longEdgesCount) {
    myCommits = commits;
    mySimpleNodes = simpleNodes;

    myNodesCount = simpleNodes.size();

    myNodeToEdgeIndex = new int[myNodesCount + 1];
    myLongEdges = new int[2 * longEdgesCount];
  }

  private void addUnderdoneEdge(int upNodeIndex, CommitId downCommitId) {
    List<Integer> upNodes = upAdjacentNodes.get(downCommitId);
    if (upNodes == null) {
      upNodes = new SmartList<Integer>();
      upAdjacentNodes.put(downCommitId, upNodes);
    }
    upNodes.add(upNodeIndex);
  }

  private void addCommitWithNotLoadParent(int nodeIndex) {
    GraphCommit<CommitId> commit = myCommits.get(nodeIndex);
    commitsWithNotLoadParent.put(commit.getId(), commit);
  }

  private void fixUnderdoneEdgeForNotLoadCommit(int upNodeIndex) {
    for (int edgeIndex = myNodeToEdgeIndex[upNodeIndex]; edgeIndex < myNodeToEdgeIndex[upNodeIndex + 1]; edgeIndex++) {
      if (myLongEdges[edgeIndex] == -1) {
        addCommitWithNotLoadParent(upNodeIndex);
        myLongEdges[edgeIndex] = LinearGraph.NOT_LOAD_COMMIT;
        return;
      }
    }
    throw new IllegalStateException("Not found underdone edge to not load commit for node: " + upNodeIndex);
  }

  private void fixUnderdoneEdge(int upNodeIndex, int downNodeIndex, CommitId downCommitId) {
    int end = myNodeToEdgeIndex[upNodeIndex + 1];

    GraphCommit<CommitId> upCommit = myCommits.get(upNodeIndex);
    List<CommitId> parentHashIndices = upCommit.getParents();

    for (int i = 0; i < parentHashIndices.size(); i++) {
      if (parentHashIndices.get(i).equals(downCommitId)) {
        int offset = parentHashIndices.size() - i;
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
    GraphCommit<CommitId> commit = myCommits.get(nodeIndex);

    List<Integer> upNodes = upAdjacentNodes.remove(commit.getId());
    if (upNodes == null)
      upNodes = Collections.emptyList();

    int edgeIndex = myNodeToEdgeIndex[nodeIndex];
    for (Integer upNodeIndex : upNodes) {
      fixUnderdoneEdge(upNodeIndex, nodeIndex, commit.getId());
      myLongEdges[edgeIndex] = upNodeIndex;
      edgeIndex++;
    }

    // down nodes
    if (!mySimpleNodes.get(nodeIndex)) {
      for (CommitId downCommitId : commit.getParents()) {
        addUnderdoneEdge(nodeIndex, downCommitId);
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

  public PermanentLinearGraphImpl build() {
    for (int nodeIndex = 0; nodeIndex < myNodesCount; nodeIndex++) {
      doStep(nodeIndex);
    }

    fixUnderdoneEdges();

    return new PermanentLinearGraphImpl(mySimpleNodes, myNodeToEdgeIndex, myLongEdges);
  }

  public Map<CommitId, GraphCommit<CommitId>> getCommitsWithNotLoadParent() {
    return commitsWithNotLoadParent;
  }
}
