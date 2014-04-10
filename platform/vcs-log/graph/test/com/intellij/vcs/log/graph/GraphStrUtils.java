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

package com.intellij.vcs.log.graph;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.facade.ContainingBranchesGetter;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GraphStrUtils {

  public static <T extends Comparable<? super T>> void appendSortList(List<T> list, StringBuilder s) {
    ArrayList<T> sorted = new ArrayList<T>(list);
    Collections.sort(sorted);
    appendList(sorted, s);
  }

  public static <T> void appendList(List<T> list, StringBuilder s) {

    boolean first = true;
    for (T element : list) {
      if (first) {
        first = false;
      } else {
        s.append(" ");
      }

      s.append(element);
    }
  }

  public static String linearGraphToStr(LinearGraph graph) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < graph.nodesCount(); nodeIndex++) {
      if (nodeIndex != 0)
        s.append("\n");

      s.append(nodeIndex);

      s.append("|-");
      appendSortList(graph.getUpNodes(nodeIndex), s);

      s.append("|-");
      appendList(graph.getDownNodes(nodeIndex), s);
    }
    return s.toString();
  }

  public static <CommitId extends Comparable<CommitId>> String commitsWithNotLoadParentMapToStr(Map<CommitId, GraphCommit<CommitId>> commitMap,
                                                                                                Function<CommitId, String> toStr) {
    List<CommitId> hashes = new ArrayList<CommitId>(commitMap.keySet());
    Collections.sort(hashes);

    StringBuilder s = new StringBuilder();
    for (int i = 0; i < hashes.size(); i++) {
      if (i != 0)
        s.append("\n");

      CommitId hash = hashes.get(i);
      GraphCommit<CommitId> commit = commitMap.get(hash);
      assertEquals(toStr.fun(hash), toStr.fun(commit.getId()));

      s.append(toStr.fun(hash)).append("|-");
      List<CommitId> parentIndices = commit.getParents();
      for (int j = 0 ; j < parentIndices.size(); j++) {
        if (j > 0)
          s.append(" ");
        s.append(toStr.fun(parentIndices.get(j)));
      }
    }
    return s.toString();
  }

  public static <CommitId> String commitsInfoToStr(PermanentCommitsInfo<CommitId> commitsInfo, Function<CommitId, String> toStr) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < commitsInfo.size(); i++) {
      if (i != 0)
        s.append("\n");

      CommitId commitId = commitsInfo.getCommitId(i);
      int commitIndex = commitsInfo.getPermanentNodeIndex(commitId);
      long timestamp = commitsInfo.getTimestamp(i);

      s.append(commitIndex).append("|-");
      s.append(toStr.fun(commitId)).append("|-");
      s.append(timestamp);
    }
    return s.toString();
  }

  public static String permanentGraphLayoutModelToStr(GraphLayout graphLayout, int nodesCount) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < nodesCount; nodeIndex++) {
      if (nodeIndex != 0)
        s.append("\n");

      s.append(graphLayout.getLayoutIndex(nodeIndex)).append("|-").append(graphLayout.getOneOfHeadNodeIndex(nodeIndex));
    }
    return s.toString();
  }

  public static String containingBranchesGetterToStr(ContainingBranchesGetter containingBranchesGetter, int nodesCount) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < nodesCount; nodeIndex++) {
      if (nodeIndex != 0)
        s.append("\n");

      List<Integer> branchNodeIndexes = new ArrayList<Integer>(containingBranchesGetter.getBranchNodeIndexes(nodeIndex));
      if (branchNodeIndexes.isEmpty()) {
        s.append("none");
        continue;
      }

      Collections.sort(branchNodeIndexes);
      boolean first = true;
      for (int branchNodeIndex : branchNodeIndexes) {
        if (first)
          first = false;
        else
          s.append(" ");

        s.append(branchNodeIndex);
      }
    }
    return s.toString();
  }
}
