/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.utils;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class BfsUtil {
  public static int getCorrespondingParent(@NotNull LiteLinearGraph graph, int startNode, int endNode, @NotNull Flags visited) {
    List<Integer> candidates = graph.getNodes(startNode, LiteLinearGraph.NodeFilter.DOWN);
    if (candidates.size() == 1) return candidates.get(0);
    if (candidates.contains(endNode)) return endNode;

    List<Queue<Integer>> queues = new ArrayList<>(candidates.size());
    for (int candidate : candidates) {
      queues.add(ContainerUtil.newLinkedList(candidate));
    }

    int emptyCount;
    visited.setAll(false);
    do {
      emptyCount = 0;
      for (Queue<Integer> queue : queues) {
        if (queue.isEmpty()) {
          emptyCount++;
        }
        else {
          boolean found = runNextBfsStep(graph, queue, visited, endNode);
          if (found) {
            return candidates.get(queues.indexOf(queue));
          }
        }
      }
    }
    while (emptyCount < queues.size());

    return candidates.get(0);
  }

  private static boolean runNextBfsStep(@NotNull LiteLinearGraph graph, @NotNull Queue<Integer> queue, @NotNull Flags visited, int target) {
    while (!queue.isEmpty()) {
      Integer node = queue.poll();
      if (!visited.get(node)) {
        visited.set(node, true);
        List<Integer> next = graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN);
        if (next.contains(target)) return true;
        queue.addAll(next);
        return false;
      }
    }
    return false;
  }
}
