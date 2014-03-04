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

package com.intellij.vcs.log.newgraph;

import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.newgraph.facade.ContainingBranchesGetter;

import java.util.*;

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

  public static String permanentGraphTorStr(PermanentGraph graph) {
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

  public static String commitsWithNotLoadParentMapToStr(Map<Integer, GraphCommit> commitMap) {
    List<Integer> hashes = new ArrayList<Integer>(commitMap.keySet());
    Collections.sort(hashes);

    StringBuilder s = new StringBuilder();
    for (int i = 0; i < hashes.size(); i++) {
      if (i != 0)
        s.append("\n");

      Integer hash = hashes.get(i);
      GraphCommit commit = commitMap.get(hash);
      assertEquals(Integer.toHexString(hash), Integer.toHexString(commit.getIndex()));

      s.append(Integer.toHexString(hash)).append("|-");
      int[] parentIndices = commit.getParentIndices();
      for (int j = 0 ; j < parentIndices.length; j++) {
        if (j > 0)
          s.append(" ");
        s.append(Integer.toHexString(parentIndices[j]));
      }
    }
    return s.toString();
  }

  public static String permanentGraphToHashIndex(PermanentGraph graph) {
    StringBuilder s = new StringBuilder();
    for (int nodeIndex = 0; nodeIndex < graph.nodesCount(); nodeIndex++) {
      if (nodeIndex != 0)
        s.append("\n");

      s.append(Integer.toHexString(graph.getHashIndex(nodeIndex)));
    }
    return s.toString();
  }

  public static String permanentGraphLayoutModelToStr(PermanentGraphLayout graphLayout, int nodesCount) {
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

      List<Integer> branchHashIndexes = new ArrayList<Integer>(containingBranchesGetter.getBranchHashIndexes(nodeIndex));
      if (branchHashIndexes.isEmpty()) {
        s.append("none");
        continue;
      }

      Collections.sort(branchHashIndexes);
      boolean first = true;
      for (int hashIndex : branchHashIndexes) {
        if (first)
          first = false;
        else
          s.append(" ");

        s.append(Integer.toHexString(hashIndex));
      }
    }
    return s.toString();
  }
}
