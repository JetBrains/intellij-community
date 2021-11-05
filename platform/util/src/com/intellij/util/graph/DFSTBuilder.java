// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * @author dsl, ven
 */
public final class DFSTBuilder<Node> {

  private final @NotNull OutboundSemiGraph<Node> myGraph;

  private final ToIntFunction<Node> myNodeToNNumber;
  // node -> node number in topological order [0..size). Independent nodes are in reversed loading order (loading order is the graph.getNodes() order)
  private final Node[] myInvN; // node number in topological order [0..size) -> node
  private Map.Entry<Node, Node> myBackEdge;

  private final Node[] allNodes;

  private Comparator<Node> myNComparator;
  private Comparator<Node> myTComparator;
  private final IntList mySCCs = new IntArrayList(); // strongly connected component sizes
  private final ToIntFunction<Node> myNodeToTNumber;
    // node -> number in scc topological order. Independent scc are in reversed loading order

  private final Node[] myInvT; // number in (enumerate all nodes scc by scc) order -> node

  /**
   * @see DFSTBuilder#DFSTBuilder(OutboundSemiGraph, Object)
   */
  public DFSTBuilder(@NotNull OutboundSemiGraph<Node> graph) {
    this(graph, null, false);
  }

  public DFSTBuilder(@NotNull OutboundSemiGraph<Node> graph,
                     @Nullable Node entryNode) {
    this(graph, entryNode, false);
  }

  @ApiStatus.Internal
  public DFSTBuilder(@NotNull OutboundSemiGraph<Node> graph,
                     @Nullable Node entryNode,
                     boolean useIdentityStrategy) {
    this.myGraph = graph;
    //noinspection unchecked
    this.allNodes = (Node[])graph.getNodes().toArray();

    if (entryNode != null) {
      int index = useIdentityStrategy ? ArrayUtil.indexOfIdentity(allNodes, entryNode) : ArrayUtil.indexOf(allNodes, entryNode);
      if (index != -1) {
        ArrayUtil.swap(allNodes, 0, index);
      }
    }
    int size = allNodes.length;
    //noinspection unchecked
    myInvN = (Node[])new Object[size];
    //noinspection unchecked
    myInvT = (Node[])new Object[size];

    if (useIdentityStrategy) {
      Reference2IntOpenHashMap<Node> nMap = new Reference2IntOpenHashMap<>(size * 2, 0.5f);
      Reference2IntOpenHashMap<Node> tMap = new Reference2IntOpenHashMap<>();
      myNodeToNNumber = nMap;
      myNodeToTNumber = tMap;
      new Tarjan(tMap::put, nMap::put, allNodes, true);
    }
    else {
      //noinspection SSBasedInspection
      Object2IntOpenHashMap<Node> nMap = new Object2IntOpenHashMap<>(size * 2, 0.5f);
      //noinspection SSBasedInspection
      Object2IntOpenHashMap<Node> tMap = new Object2IntOpenHashMap<>();
      myNodeToNNumber = nMap;
      myNodeToTNumber = tMap;
      new Tarjan(tMap::put, nMap::put, allNodes, false);
    }
  }

  private static final class TarjanFrame<Node> {
    private final int nodeI;
    private final Node[] allNodes;
    private final int[] out;
    int nextUnexploredIndex;

    TarjanFrame(int nodeI, Node[] allNodes, int[] out) {
      this.nodeI = nodeI;
      this.allNodes = allNodes;
      this.out = out;
    }

    @Override
    public String toString() {
      StringBuilder o = new StringBuilder();
      o.append(allNodes[nodeI]).append(" -> [");
      for (int id : out) {
        o.append(allNodes[id]).append(", ");
      }
      return o.append(']').toString();
    }
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

    private final Deque<TarjanFrame<Node>> frames = new ArrayDeque<>(); // recursion stack
    private int dfsIndex;
    private int sccsSizeCombined;
    private final IntList topo = new IntArrayList(index.length); // nodes in reverse topological order
    private final ToIntFunction<? super Node> myNodeIndex;

    private Tarjan(ObjIntConsumer<Node> putTNumber, ObjIntConsumer<Node> putNNumber, Node[] allNodes, boolean useIdentityStrategy) {
      myNodeIndex = useIdentityStrategy ?
                    createReference2IntMap(allNodes) :
                    createObject2IntMap(allNodes);
      Arrays.fill(index, -1);
      build(putTNumber, putNNumber, allNodes);
    }

    private void build(ObjIntConsumer<Node> putTNumber,
                       ObjIntConsumer<Node> putNNumber,
                       Node[] allNodes) {
      for (int i = 0; i < index.length; i++) {
        if (index[i] != -1) {
          continue;
        }

        frames.addLast(new TarjanFrame<>(i, allNodes, buildOuts(allNodes[i])));
        List<List<Node>> sccs = new ArrayList<>();

        strongConnect(sccs, allNodes);

        for (List<Node> scc : sccs) {
          int sccSize = scc.size();

          mySCCs.add(sccSize);
          int sccBase = index.length - sccsSizeCombined - sccSize;

          // root node should be first in scc for some reason
          Node rootNode = allNodes[i];
          int rIndex = scc.indexOf(rootNode);
          if (rIndex != -1) {
            Node e1 = scc.get(rIndex);
            Node e2 = scc.get(0);
            scc.set(rIndex, e2);
            scc.set(0, e1);
          }

          for (int j = 0; j < scc.size(); j++) {
            Node sccNode = scc.get(j);
            int tIndex = sccBase + j;
            myInvT[tIndex] = sccNode;
            putTNumber.accept(sccNode, tIndex);
          }
          sccsSizeCombined += sccSize;
        }
      }

      for (int i = 0; i < topo.size(); i++) {
        int nodeI = topo.getInt(i);
        Node node = allNodes[nodeI];

        putNNumber.accept(node, index.length - 1 - i);
        myInvN[index.length - 1 - i] = node;
      }

      // have to place SCCs in topological order too
      for (int i = 0, j = mySCCs.size() - 1; i < j; i++, j--) {
        int tmp = mySCCs.getInt(i);
        mySCCs.set(i, mySCCs.getInt(j));
        mySCCs.set(j, tmp);
      }
    }

    private void strongConnect(@NotNull List<? super List<Node>> sccs, Node[] allNodes) {
      int successor = -1;
      nextNode:
      while (!frames.isEmpty()) {
        TarjanFrame<Node> pair = frames.peekLast();
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
            frames.addLast(new TarjanFrame<>(nextI, allNodes, buildOuts(allNodes[nextI])));
            continue nextNode;
          }
          if (isOnStack[nextI]) {
            lowLink[i] = Math.min(lowLink[i], index[nextI]);

            if (myBackEdge == null) {
              myBackEdge = new AbstractMap.SimpleImmutableEntry<>(allNodes[nextI], allNodes[i]);
            }
          }
        }
        frames.removeLast();
        topo.add(i);
        // we are really back, pop a scc
        if (lowLink[i] == index[i]) {
          // found yer
          List<Node> scc = new ArrayList<>();
          int pushedI;
          do {
            pushedI = nodesOnStack.popInt();
            Node pushed = allNodes[pushedI];
            isOnStack[pushedI] = false;
            scc.add(pushed);
          }
          while (pushedI != i);
          sccs.add(scc);
        }
      }
    }

    private int[] buildOuts(@NotNull Node node) {
      IntList list = new IntArrayList();
      Iterator<Node> out = myGraph.getOut(node);
      while (out.hasNext()) {
        list.add(myNodeIndex.applyAsInt(out.next()));
      }
      return list.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : list.toIntArray();
    }

    @ReviseWhenPortedToJDK(value = "8", description = "define static")
    private @NotNull Object2IntMap<Node> createObject2IntMap(Node[] nodes) {
      Object2IntMap<Node> result = new Object2IntOpenHashMap<>(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        result.put(nodes[i], i);
      }
      return result;
    }

    @ReviseWhenPortedToJDK(value = "8", description = "define static")
    private @NotNull Reference2IntOpenHashMap<Node> createReference2IntMap(Node[] nodes) {
      Reference2IntOpenHashMap<Node> nodeIndex = new Reference2IntOpenHashMap<>(nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        nodeIndex.put(nodes[i], i);
      }
      return nodeIndex;
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
        myNComparator = Comparator.comparingInt(myNodeToNNumber);
      }
      return myNComparator;
    }
    else {
      if (myTComparator == null) {
        myTComparator = Comparator.comparingInt(myNodeToTNumber);
      }
      return myTComparator;
    }
  }

  @Nullable
  public Map.Entry<Node, Node> getCircularDependency() {
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

  public @NotNull List<Node> getSortedNodes() {
    Node[] result = allNodes.clone();
    Arrays.sort(result, comparator());
    return Arrays.asList(result);
  }
}