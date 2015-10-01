/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.graph;

import com.intellij.openapi.util.Couple;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntStack;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 *  @author dsl, ven
 */
public class DFSTBuilder<Node> {
  private final Graph<Node> myGraph;
  private final TObjectIntHashMap<Node> myNodeToNNumber; // node -> node number in topological order [0..size). Independent nodes are in reversed loading order (loading order is the graph.getNodes() order)
  private final Node[] myInvN; // node number in topological order [0..size) -> node
  private Couple<Node> myBackEdge;

  private Comparator<Node> myComparator;
  private final TIntArrayList mySCCs = new TIntArrayList(); // strongly connected component sizes
  private final TObjectIntHashMap<Node> myNodeToTNumber = new TObjectIntHashMap<Node>(); // node -> number in scc topological order. Independent scc are in reversed loading order

  private final Node[] myInvT; // number in (enumerate all nodes scc by scc) order -> node
  private final Node[] myAllNodes;

  public DFSTBuilder(@NotNull Graph<Node> graph) {
    myAllNodes = (Node[])graph.getNodes().toArray();
    myGraph = graph;
    int size = graph.getNodes().size();
    myNodeToNNumber = new TObjectIntHashMap<Node>(size * 2, 0.5f);
    myInvN = (Node[])new Object[size];
    myInvT = (Node[])new Object[size];
    new Tarjan().build();
  }

  @Deprecated
  public void buildDFST() {
  }

  /**
   * Tarjan strong-connect-components search algorithm.
   * See e.g. <a href="https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm</a><br>
   * This implementation differs from the canonical one above by<br>
   * <ul>
   *   <li>- being not recursive</li>
   *   <li>- computing also topological order during the same single pass</li>
   * </ul>
   */
  private class Tarjan {
    private final int[] lowLink = new int[myInvN.length];
    private final int[] index = new int[myInvN.length];

    private final IntStack nodesOnStack = new IntStack();
    private final boolean[] isOnStack = new boolean[index.length];

    private class Frame {
      public Frame(int nodeI) {
        this.nodeI = nodeI;
        Iterator<Node> outNodes = myGraph.getOut(myAllNodes[nodeI]);
        TIntArrayList list = new TIntArrayList();

        while (outNodes.hasNext()) {
          Node node = outNodes.next();
          list.add(nodeIndex.get(node));
        }
        out = list.toNativeArray();
      }

      private final int nodeI;
      private final int[] out;
      private int nextUnexploredIndex;

      @Override
      public String toString() {
        final StringBuilder o = new StringBuilder();
        for (int id : out) {
          o.append(myAllNodes[id] + ", ");
        }
        return myAllNodes[nodeI] + " -> [" + o + "]";
      }
    }

    private final Stack<Frame> frames = new Stack<Frame>(); // recursion stack
    private final TObjectIntHashMap<Node> nodeIndex = new TObjectIntHashMap<Node>();
    private int dfsIndex;
    private int sccsSizeCombined;
    private final TIntArrayList topo = new TIntArrayList(index.length); // nodes in reverse topological order

    private void build() {
      Arrays.fill(index, -1);
      for (int i = 0; i < myAllNodes.length; i++) {
        Node node = myAllNodes[i];
        nodeIndex.put(node, i);
      }
      for (int i = 0; i < index.length; i++) {
        if (index[i] == -1) {
          frames.push(new Frame(i));
          List<List<Node>> sccs = new ArrayList<List<Node>>();

          strongConnect(sccs);

          for (List<Node> scc : sccs) {
            int sccSize = scc.size();

            mySCCs.add(sccSize);
            int sccBase = index.length - sccsSizeCombined - sccSize;

            // root node should be first in scc for some reason
            Node rootNode = myAllNodes[i];
            int rIndex = scc.indexOf(rootNode);
            if (rIndex != -1) {
              ContainerUtil.swapElements(scc, rIndex, 0);
            }

            for (int j = 0; j < scc.size(); j++) {
              Node sccNode = scc.get(j);
              int tIndex = sccBase + j;
              myInvT[tIndex] = sccNode;
              myNodeToTNumber.put(sccNode, tIndex);
            }
            sccsSizeCombined += sccSize;
          }
        }
      }

      for (int i = 0; i < topo.size(); i++) {
        int nodeI = topo.get(i);
        Node node = myAllNodes[nodeI];

        myNodeToNNumber.put(node, index.length - 1 - i);
        myInvN[index.length - 1 - i] = node;
      }
      mySCCs.reverse(); // have to place sccs in topological order too
    }

    private void strongConnect(@NotNull List<List<Node>> sccs) {
      int successor = -1;
      nextNode:
      while (!frames.isEmpty()) {
        Frame pair = frames.peek();
        int i = pair.nodeI;

        // we have returned to the node
        if (index[i] == -1) {
          // actually we visit node first time, prepare
          index[i] = dfsIndex;
          lowLink[i] = dfsIndex;
          dfsIndex++;
          nodesOnStack.push(i);
          isOnStack[i] = true;
        }
        if (ArrayUtil.indexOf(pair.out, successor) != -1) {
          lowLink[i] = Math.min(lowLink[i], lowLink[successor]);
        }
        successor = i;

        // if unexplored children left, dfs there
        while (pair.nextUnexploredIndex<pair.out.length) {
          int nextI = pair.out[pair.nextUnexploredIndex++];
          if (index[nextI] == -1) {
            frames.push(new Frame(nextI));
            continue nextNode;
          }
          if (isOnStack[nextI]) {
            lowLink[i] = Math.min(lowLink[i], index[nextI]);

            if (myBackEdge == null) {
              myBackEdge = Couple.of(myAllNodes[nextI], myAllNodes[i]);
            }
          }
        }
        frames.pop();
        topo.add(i);
        // we are really back, pop a scc
        if (lowLink[i] == index[i]) {
          // found yer
          List<Node> scc = new ArrayList<Node>();
          int pushedI;
          do {
            pushedI = nodesOnStack.pop();
            Node pushed = myAllNodes[pushedI];
            isOnStack[pushedI] = false;
            scc.add(pushed);
          }
          while (pushedI != i);
          sccs.add(scc);
        }
      }
    }
  }

  @NotNull
  public Comparator<Node> comparator() {
    if (myComparator == null) {
      final TObjectIntHashMap<Node> map = isAcyclic() ? myNodeToNNumber : myNodeToTNumber;
      myComparator = new Comparator<Node>() {
        @Override
        public int compare(@NotNull Node t, @NotNull Node t1) {
          return map.get(t) - map.get(t1);
        }
      };
    }
    return myComparator;
  }

  public Couple<Node> getCircularDependency() {
    return myBackEdge;
  }

  public boolean isAcyclic() {
    return getCircularDependency() == null;
  }

  @NotNull
  public Node getNodeByNNumber(final int n) {
    return myInvN[n];
  }

  @NotNull
  public Node getNodeByTNumber(final int n) {
    return myInvT[n];
  }

  /**
   * @return the list containing the number of nodes in strongly connected components.
   * Respective nodes could be obtained via {@link #getNodeByTNumber(int)}.
   */
  @NotNull
  public TIntArrayList getSCCs() {
    return mySCCs;
  }

  @NotNull
  public List<Node> getSortedNodes() {
    List<Node> result = new ArrayList<Node>(myGraph.getNodes());
    Collections.sort(result, comparator());
    return result;
  }
}
