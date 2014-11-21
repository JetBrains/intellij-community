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
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.IntStack;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

// better DfsUtil, with ... uh ... well ... you know
public class GraphVisitorAlgorithm {
  private static final Logger LOG = Logger.getInstance(GraphVisitorAlgorithm.class);

  private static final int NODE_NOT_FOUND = -1;
  private final boolean myBackwards;

  public GraphVisitorAlgorithm(boolean backwards) {
    myBackwards = backwards;
  }

  public GraphVisitorAlgorithm() {
    this(false);
  }

  public void visitGraph(@NotNull final LinearGraph graph, @NotNull GraphLayout layout, @NotNull GraphVisitor visitor) {
    final BitSetFlags visited = new BitSetFlags(graph.nodesCount(), false);
    for (int head : layout.getHeadNodeIndex()) {
      IntStack stack = new IntStack();

      visited.set(head, true);
      stack.push(head);
      visitor.enterSubtree(head, visited);

      while (!stack.empty()) {
        int nextNode = nextNode(graph, stack.peek(), visited);
        if (nextNode != NODE_NOT_FOUND) {
          visited.set(nextNode, true);
          stack.push(nextNode);
          visitor.enterSubtree(nextNode, visited);
        }
        else {
          visitor.leaveSubtree(stack.pop(), visited);
        }
      }

      visitor.leaveSubtree(head, visited);
    }

    if (Boolean.getBoolean("idea.is.internal")) { // do not want to depend on Application here
      for (int i = 0; i < visited.size(); i++) {
        if (!visited.get(i)) {
          LOG.warn("Missed node " + i);
        }
      }
    }
  }

  public void visitSubgraph(@NotNull final LinearGraph graph, @NotNull GraphVisitor visitor, int start, int depth) {
    final BitSetFlags visited = new BitSetFlags(graph.nodesCount(), false);

    IntStack stack = new IntStack();

    visited.set(start, true);
    stack.push(start);
    visitor.enterSubtree(start, visited);

    while (!stack.empty()) {
      int nextNode = simpleNextNode(graph, stack.peek(), visited);
      if (nextNode != NODE_NOT_FOUND && stack.size() < depth) {
        visited.set(nextNode, true);
        stack.push(nextNode);
        visitor.enterSubtree(nextNode, visited);
      }
      else {
        visitor.leaveSubtree(stack.pop(), visited);
      }
    }

    visitor.leaveSubtree(start, visited);
  }

  private int simpleNextNode(@NotNull LinearGraph graph, int currentNode, @NotNull BitSetFlags visited) {
    List<Integer> downNodes = getDownNodes(graph, currentNode);
    if (myBackwards) Collections.reverse(downNodes);

    for (int downNode : downNodes) {
      if (!visited.get(downNode)) {
        return downNode;
      }
    }
    return NODE_NOT_FOUND;
  }

  private int nextNode(@NotNull LinearGraph graph, int currentNode, @NotNull BitSetFlags visited) {
    List<Integer> downNodes = getDownNodes(graph, currentNode);
    if (myBackwards) Collections.reverse(downNodes);

    for (int downNode : downNodes) {
      if (!visited.get(downNode)) {

        boolean canGoThere = true;
        List<Integer> upNodes = getUpNodes(graph, downNode);
        for (int upNode : upNodes) {
          if (!visited.get(upNode)) {
            canGoThere = false;
            break;
          }
        }

        if (canGoThere) {
          return downNode;
        }
      }
    }
    return NODE_NOT_FOUND;
  }

  public interface GraphVisitor {
    void enterSubtree(int nodeIndex, BitSetFlags visited);

    void leaveSubtree(int nodeIndex, BitSetFlags visited);
  }

  public abstract static class SimpleVisitor implements GraphVisitor {
    public abstract void visitNode(int nodeIndex);

    @Override
    public void enterSubtree(int nodeIndex, BitSetFlags visited) {
      visitNode(nodeIndex);
    }

    @Override
    public void leaveSubtree(int nodeIndex, BitSetFlags visited) {
    }
  }
}
