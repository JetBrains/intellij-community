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

package com.intellij.vcs.log.graph.utils;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.IntStack;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

public class DfsUtil {
  public interface NextNode {
    int NODE_NOT_FOUND = -1;
    int EXIT = -10;

    int fun(int currentNode);
  }

  public interface NodeVisitor {
    void enterNode(int node, int previousNode, boolean travelDirection);

    void exitNode(int node);
  }

  /*
   * Depth-first walk for a graph. For each node, walks both into upward and downward siblings.
   * Tries to preserve direction of travel: when a node is entered from up-sibling, goes to the down-siblings first.
   * Then goes to the other up-siblings.
   * And when a node is entered from down-sibling, goes to the up-siblings first.
   * Then goes to the other down-siblings.
   * When a node is entered the first time, enterNode is called.
   * When a all the siblings of the node are visited, exitNode is called.
   */
  public static void walk(@NotNull LiteLinearGraph graph, int start, @NotNull NodeVisitor visitor) {
    BitSetFlags visited = new BitSetFlags(graph.nodesCount(), false);

    Stack<Pair<Integer, Boolean>> stack = new Stack<>();
    stack.push(new Pair<>(start, true)); // commit + direction of travel

    outer:
    while (!stack.empty()) {
      int currentNode = stack.peek().first;
      boolean down = stack.peek().second;
      if (!visited.get(currentNode)) {
        visited.set(currentNode, true);
        visitor.enterNode(currentNode, getPreviousNode(stack), down);
      }

      for (int nextNode: graph.getNodes(currentNode, down ? LiteLinearGraph.NodeFilter.DOWN : LiteLinearGraph.NodeFilter.UP)) {
        if (!visited.get(nextNode)) {
          stack.push(new Pair<>(nextNode, down));
          continue outer;
        }
      }

      for (int nextNode: graph.getNodes(currentNode, down ? LiteLinearGraph.NodeFilter.UP : LiteLinearGraph.NodeFilter.DOWN)) {
        if (!visited.get(nextNode)) {
          stack.push(new Pair<>(nextNode, !down));
          continue outer;
        }
      }

      visitor.exitNode(currentNode);
      stack.pop();
    }
  }

  private static int getPreviousNode(@NotNull Stack<Pair<Integer, Boolean>> stack) {
    if (stack.size() < 2) {
      return NextNode.NODE_NOT_FOUND;
    }
    return stack.get(stack.size() - 2).first;
  }

  public static void walk(int startRowIndex, @NotNull NextNode nextNodeFun) {
    IntStack stack = new IntStack();
    stack.push(startRowIndex);

    while (!stack.empty()) {
      int nextNode = nextNodeFun.fun(stack.peek());
      if (nextNode == NextNode.EXIT) return;
      if (nextNode != NextNode.NODE_NOT_FOUND) {
        stack.push(nextNode);
      }
      else {
        stack.pop();
      }
    }
    stack.clear();
  }

  public static boolean isAncestor(@NotNull LiteLinearGraph graph, int lowerNode, int upperNode) {
    BitSetFlags visited = new BitSetFlags(graph.nodesCount(), false);

    Ref<Boolean> result = Ref.create(false);
    walk(lowerNode, currentNode -> {
      visited.set(currentNode, true);

      if (currentNode == upperNode) {
        result.set(true);
        return NextNode.EXIT;
      }
      if (currentNode > upperNode) {
        for (int nextNode: graph.getNodes(currentNode, LiteLinearGraph.NodeFilter.UP)) {
          if (!visited.get(nextNode)) {
            return nextNode;
          }
        }
      }

      return NextNode.NODE_NOT_FOUND;
    });

    return result.get();
  }
}
