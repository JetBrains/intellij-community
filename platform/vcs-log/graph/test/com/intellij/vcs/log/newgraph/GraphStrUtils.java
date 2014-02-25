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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
