// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.impl.print.EdgesInRowGenerator;
import com.intellij.vcs.log.graph.impl.print.GraphElementComparatorByLayoutIndex;
import com.intellij.vcs.log.graph.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.toChar;

public final class GraphStrUtils {

  public static final Comparator<GraphElement> GRAPH_ELEMENT_COMPARATOR =
    new GraphElementComparatorByLayoutIndex(nodeIndex -> 0);

  public static <CommitId> String commitsInfoToStr(PermanentCommitsInfo<CommitId> commitsInfo, int size, Function<? super CommitId, String> toStr) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < size; i++) {
      if (i != 0) s.append("\n");

      CommitId commitId = commitsInfo.getCommitId(i);
      int commitIndex = commitsInfo.getNodeId(commitId);
      long timestamp = commitsInfo.getTimestamp(i);

      s.append(commitIndex).append(CommitParser.SEPARATOR);
      s.append(toStr.fun(commitId)).append(CommitParser.SEPARATOR);
      s.append(timestamp);
    }
    return s.toString();
  }

  public static String permanentGraphLayoutModelToStr(GraphLayout graphLayout, int nodesCount) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < nodesCount; nodeIndex++) {
      if (nodeIndex != 0) s.append("\n");

      s.append(graphLayout.getLayoutIndex(nodeIndex)).append(CommitParser.SEPARATOR).append(graphLayout.getOneOfHeadNodeIndex(nodeIndex));
    }
    return s.toString();
  }

  public static String containingBranchesGetterToStr(ReachableNodes reachableNodes, Set<Integer> branches, int nodesCount) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < nodesCount; nodeIndex++) {
      if (nodeIndex != 0) s.append("\n");

      List<Integer> branchNodeIndexes = new ArrayList<>(reachableNodes.getContainingBranches(nodeIndex, branches));
      if (branchNodeIndexes.isEmpty()) {
        s.append("none");
        continue;
      }

      Collections.sort(branchNodeIndexes);
      boolean first = true;
      for (int branchNodeIndex : branchNodeIndexes) {
        if (first) {
          first = false;
        }
        else {
          s.append(" ");
        }

        s.append(branchNodeIndex);
      }
    }
    return s.toString();
  }

  public static String edgesInRowToStr(@NotNull EdgesInRowGenerator edgesInRowGenerator, int nodesCount) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < nodesCount; i++) {
      if (i > 0) s.append("\n");
      Set<GraphEdge> edgesInRow = edgesInRowGenerator.getEdgesInRow(i);
      s.append(edgesToStr(edgesInRow));
    }
    return s.toString();
  }

  public static String edgesToStr(@NotNull Set<GraphEdge> edges) {
    if (edges.isEmpty()) return "none";

    List<GraphEdge> sortedEdges = new ArrayList<>(edges);
    Collections.sort(sortedEdges, GRAPH_ELEMENT_COMPARATOR);

    return StringUtil.join(sortedEdges,
                           graphEdge -> graphEdge.getUpNodeIndex() + "_" + graphEdge.getDownNodeIndex() + "_" + toChar(graphEdge.getType()), " ");
  }
}
