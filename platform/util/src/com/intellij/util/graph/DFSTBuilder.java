// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.openapi.util.Couple;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl, ven
 */
public final class DFSTBuilder<Node> {
  private final OutboundSemiGraph<Node> myGraph;
  private final Object2IntMap<Node> myNodeToNNumber; // node -> node number in topological order [0..size). Independent nodes are in reversed loading order (loading order is the graph.getNodes() order)
  private final Node[] myInvN; // node number in topological order [0..size) -> node
  private Couple<Node> myBackEdge;

  private Comparator<Node> myNComparator;
  private Comparator<Node> myTComparator;
  private final IntList mySCCs = new IntArrayList(); // strongly connected component sizes
  private final Object2IntMap<Node> myNodeToTNumber = new Object2IntOpenHashMap<>(); // node -> number in scc topological order. Independent scc are in reversed loading order

  private final Node[] myInvT; // number in (enumerate all nodes scc by scc) order -> node
  private final Node[] myAllNodes;

  /**
   * @see DFSTBuilder#DFSTBuilder(OutboundSemiGraph, Object)
   */
  public DFSTBuilder(@NotNull Graph<Node> graph) {
    this(graph, null);
  }

  /**
   * @see DFSTBuilder#DFSTBuilder(OutboundSemiGraph, Object)
   */
  public DFSTBuilder(@NotNull Graph<Node> graph, @Nullable Node entryNode) {
    this((OutboundSemiGraph<Node>)graph, entryNode);
  }

  /**
   * @see DFSTBuilder#DFSTBuilder(OutboundSemiGraph, Object)
   */
  public DFSTBuilder(@NotNull OutboundSemiGraph<Node> graph) {
    this(graph, null);
  }

  /**
   * @param entryNode is a first node for Tarjan's algorithm. Different entry nodes produce different node numbers in topological ordering.
   *                  if all nodes of the graph is reachable from the entry node and the entry node doesn't have incoming edges then
   *                  passing the entry node could be used for finding "natural" back edges (like a loop back edge)
   */
  public DFSTBuilder(@NotNull OutboundSemiGraph<Node> graph, @Nullable Node entryNode) {
    //noinspection unchecked
    myAllNodes = (Node[])graph.getNodes().toArray();
    if (entryNode != null) {
      int index = ArrayUtil.indexOf(myAllNodes, entryNode);
      if (index != -1) {
        ArrayUtil.swap(myAllNodes, 0, index);
      }
    }
    myGraph = graph;
    int size = graph.getNodes().size();
    myNodeToNNumber = new Object2IntOpenHashMap<>(size * 2, 0.5f);
    //noinspection unchecked
    myInvN = (Node[])new Object[size];
    //noinspection unchecked
    myInvT = (Node[])new Object[size];
    new Tarjan().build();
  }

  /**
   * Tarjan's strongly connected components search algorithm
   * (<a href="https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">Wikipedia article</a>).<br>
   * This implementation differs from the canonical one above by:<br>
   * <ul>
   *   <li>being non-recursive</li>
   *   <li>also computing a topological order during the same single pass</li>
   * </ul>
   */
  private final class Tarjan {
    private final int[] lowLink = new int[myInvN.length];
    private final int[] index = new int[myInvN.length];

    private final IntStack nodesOnStack = new IntArrayList();
    private final boolean[] isOnStack = new boolean[index.length];

    private final class Frame {
      Frame(int nodeI) {
        this.nodeI = nodeI;
        Iterator<Node> outNodes = myGraph.getOut(myAllNodes[nodeI]);
        IntList list = new IntArrayList();
        while (outNodes.hasNext()) {
          Node node = outNodes.next();
          list.add(nodeIndex.getInt(node));
        }
        out = list.toIntArray();
      }

      private final int nodeI;
      private final int[] out;
      private int nextUnexploredIndex;

      @Override
      public String toString() {
        StringBuilder o = new StringBuilder();
        o.append(myAllNodes[nodeI]).append(" -> [");
        for (int id : out) {
          o.append(myAllNodes[id]).append(", ");
        }
        return o.append(']').toString();
      }
    }

    private final Stack<Frame> frames = new Stack<>(); // recursion stack
    private final Object2IntMap<Node> nodeIndex = new Object2IntOpenHashMap<>();
    private int dfsIndex;
    private int sccsSizeCombined;
    private final IntList topo = new IntArrayList(index.length); // nodes in reverse topological order

    private void build() {
      Arrays.fill(index, -1);
      for (int i = 0; i < myAllNodes.length; i++) {
        Node node = myAllNodes[i];
        nodeIndex.put(node, i);
      }
      for (int i = 0; i < index.length; i++) {
        if (index[i] != -1) {
          continue;
        }

        frames.push(new Frame(i));
        List<List<Node>> sccs = new ArrayList<>();

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

      for (int i = 0; i < topo.size(); i++) {
        int nodeI = topo.getInt(i);
        Node node = myAllNodes[nodeI];

        myNodeToNNumber.put(node, index.length - 1 - i);
        myInvN[index.length - 1 - i] = node;
      }

      // have to place SCCs in topological order too
      for (int i = 0, j = mySCCs.size() - 1; i < j; i++, j--) {
        int tmp = mySCCs.getInt(i);
        mySCCs.set(i, mySCCs.getInt(j));
        mySCCs.set(j, tmp);
      }
    }

    private void strongConnect(@NotNull List<? super List<Node>> sccs) {
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
        while (pair.nextUnexploredIndex < pair.out.length) {
          int nextI = pair.out[pair.nextUnexploredIndex++];
          if (index[nextI] == -1) {
            frames.push(new Frame(nextI));
            continue nextNode;
          }
          if (isOnStack[nextI]) {
            lowLink[i] = Math.min(lowLink[i], index[nextI]);

            if (myBackEdge == null) {
              myBackEdge = new Couple<>(myAllNodes[nextI], myAllNodes[i]);
            }
          }
        }
        frames.pop();
        topo.add(i);
        // we are really back, pop a scc
        if (lowLink[i] == index[i]) {
          // found yer
          List<Node> scc = new ArrayList<>();
          int pushedI;
          do {
            pushedI = nodesOnStack.popInt();
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
    return comparator(isAcyclic());
  }

  /**
   * @param useNNumber if true then a node number in topological ordering will be used for comparison
   *           otherwise a node number in scc topological order will be used
   */
  @NotNull
  public Comparator<Node> comparator(boolean useNNumber) {
    if (useNNumber) {
      if (myNComparator == null) {
        myNComparator = Comparator.comparingInt(myNodeToNNumber::getInt);
      }
      return myNComparator;
    }
    else {
      if (myTComparator == null) {
        myTComparator = Comparator.comparingInt(myNodeToTNumber::getInt);
      }
      return myTComparator;
    }
  }

  @Nullable
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
  public IntList getSCCs() {
    return mySCCs;
  }

  @NotNull
  public Collection<Collection<Node>> getComponents() {
    IntList componentSizes = getSCCs();
    if (componentSizes.isEmpty()) {
      return Collections.emptyList();
    }

    return new MyCollection<Collection<Node>>(componentSizes.size()) {
      @NotNull
      @Override
      public Iterator<Collection<Node>> iterator() {
        return new MyIterator<Collection<Node>>(componentSizes.size()) {
          private int offset;

          @Override
          protected Collection<Node> get(int i) {
            final int cSize = componentSizes.getInt(i);
            final int cOffset = offset;
            if (cSize == 0) {
              return Collections.emptyList();
            }

            offset += cSize;
            return new MyCollection<Node>(cSize) {
              @NotNull
              @Override
              public Iterator<Node> iterator() {
                return new MyIterator<Node>(cSize) {
                  @Override
                  public Node get(int i) {
                    return getNodeByTNumber(cOffset + i);
                  }
                };
              }
            };
          }
        };
      }
    };
  }

  private abstract static class MyCollection<T> extends AbstractCollection<T> {
    private final int size;

    protected MyCollection(int size) {
      this.size = size;
    }

    @Override
    public int size() {
      return size;
    }
  }

  private abstract static class MyIterator<T> implements Iterator<T> {
    private final int size;
    private int i;

    protected MyIterator(int size) {
      this.size = size;
    }

    @Override
    public boolean hasNext() {
      return i < size;
    }

    @Override
    public T next() {
      if (i == size) {
        throw new NoSuchElementException();
      }
      return get(i++);
    }

    protected abstract T get(int i);

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @NotNull
  public List<Node> getSortedNodes() {
    List<Node> result = new ArrayList<>(myGraph.getNodes());
    result.sort(comparator());
    return result;
  }
}