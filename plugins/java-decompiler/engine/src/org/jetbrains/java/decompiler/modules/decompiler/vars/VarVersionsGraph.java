// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.decompose.GenericDominatorEngine;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraph;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;

public class VarVersionsGraph {
  public final VBStyleCollection<VarVersionNode, VarVersionPair> nodes = new VBStyleCollection<>();

  private GenericDominatorEngine engine;

  public VarVersionNode createNode(VarVersionPair ver) {
    VarVersionNode node;
    nodes.addWithKey(node = new VarVersionNode(ver.var, ver.version), ver);
    return node;
  }

  public void addNodes(Collection<VarVersionNode> colnodes, Collection<VarVersionPair> colpaars) {
    nodes.addAllWithKey(colnodes, colpaars);
  }

  public boolean isDominatorSet(VarVersionNode node, Set<VarVersionNode> domnodes) {
    if (domnodes.size() == 1) {
      return engine.isDominator(node, domnodes.iterator().next());
    }
    else {
      Set<VarVersionNode> marked = new HashSet<>();

      if (domnodes.contains(node)) {
        return true;
      }

      List<VarVersionNode> lstNodes = new LinkedList<>();
      lstNodes.add(node);

      while (!lstNodes.isEmpty()) {
        VarVersionNode nd = lstNodes.remove(0);
        if (marked.contains(nd)) {
          continue;
        }
        else {
          marked.add(nd);
        }

        if (nd.preds.isEmpty()) {
          return false;
        }

        for (VarVersionEdge edge : nd.preds) {
          VarVersionNode pred = edge.source;
          if (!marked.contains(pred) && !domnodes.contains(pred)) {
            lstNodes.add(pred);
          }
        }
      }
    }

    return true;
  }

  public void initDominators() {
    Set<VarVersionNode> roots = new HashSet<>();

    for (VarVersionNode node : nodes) {
      if (node.preds.isEmpty()) {
        roots.add(node);
      }
    }

    engine = new GenericDominatorEngine(new IGraph() {
      public List<? extends IGraphNode> getReversePostOrderList() {
        return getReversedPostOrder(roots);
      }

      public Set<? extends IGraphNode> getRoots() {
        return new HashSet<IGraphNode>(roots);
      }
    });

    engine.initialize();
  }

  private static List<VarVersionNode> getReversedPostOrder(Collection<VarVersionNode> roots) {
    List<VarVersionNode> lst = new LinkedList<>();
    Set<VarVersionNode> setVisited = new HashSet<>();

    for (VarVersionNode root : roots) {
      List<VarVersionNode> lstTemp = new LinkedList<>();
      addToReversePostOrderListIterative(root, lstTemp, setVisited);
      lst.addAll(lstTemp);
    }

    return lst;
  }

  private static void addToReversePostOrderListIterative(VarVersionNode root, List<VarVersionNode> lst, Set<VarVersionNode> setVisited) {
    Map<VarVersionNode, List<VarVersionEdge>> mapNodeSuccs = new HashMap<>();
    LinkedList<VarVersionNode> stackNode = new LinkedList<>();
    LinkedList<Integer> stackIndex = new LinkedList<>();

    stackNode.add(root);
    stackIndex.add(0);

    while (!stackNode.isEmpty()) {
      VarVersionNode node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<VarVersionEdge> lstSuccs = mapNodeSuccs.computeIfAbsent(node, n -> new ArrayList<>(n.succs));
      for (; index < lstSuccs.size(); index++) {
        VarVersionNode succ = lstSuccs.get(index).dest;

        if (!setVisited.contains(succ)) {
          stackIndex.add(index + 1);
          stackNode.add(succ);
          stackIndex.add(0);
          break;
        }
      }

      if (index == lstSuccs.size()) {
        lst.add(0, node);
        stackNode.removeLast();
      }
    }
  }
}