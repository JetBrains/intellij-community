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
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.decompose.GenericDominatorEngine;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraph;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;


public class VarVersionsGraph {

  public int counter = 0;

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

  public boolean isDominatorSet(VarVersionNode node, HashSet<VarVersionNode> domnodes) {

    if (domnodes.size() == 1) {
      return engine.isDominator(node, domnodes.iterator().next());
    }
    else {

      HashSet<VarVersionNode> marked = new HashSet<>();

      if (domnodes.contains(node)) {
        return true;
      }

      LinkedList<VarVersionNode> lstNodes = new LinkedList<>();
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

    final HashSet<VarVersionNode> roots = new HashSet<>();

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

  private static LinkedList<VarVersionNode> getReversedPostOrder(Collection<VarVersionNode> roots) {

    LinkedList<VarVersionNode> lst = new LinkedList<>();
    HashSet<VarVersionNode> setVisited = new HashSet<>();

    for (VarVersionNode root : roots) {

      LinkedList<VarVersionNode> lstTemp = new LinkedList<>();
      addToReversePostOrderListIterative(root, lstTemp, setVisited);

      lst.addAll(lstTemp);
    }

    return lst;
  }

  private static void addToReversePostOrderListIterative(VarVersionNode root, List<VarVersionNode> lst, HashSet<VarVersionNode> setVisited) {

    HashMap<VarVersionNode, List<VarVersionEdge>> mapNodeSuccs = new HashMap<>();

    LinkedList<VarVersionNode> stackNode = new LinkedList<>();
    LinkedList<Integer> stackIndex = new LinkedList<>();

    stackNode.add(root);
    stackIndex.add(0);

    while (!stackNode.isEmpty()) {

      VarVersionNode node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<VarVersionEdge> lstSuccs = mapNodeSuccs.get(node);
      if (lstSuccs == null) {
        mapNodeSuccs.put(node, lstSuccs = new ArrayList<>(node.succs));
      }

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
