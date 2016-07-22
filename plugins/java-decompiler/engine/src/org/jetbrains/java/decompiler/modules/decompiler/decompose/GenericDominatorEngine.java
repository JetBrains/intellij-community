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
package org.jetbrains.java.decompiler.modules.decompiler.decompose;

import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.List;
import java.util.Set;

public class GenericDominatorEngine {

  private final IGraph graph;

  private final VBStyleCollection<IGraphNode, IGraphNode> colOrderedIDoms = new VBStyleCollection<>();

  private Set<? extends IGraphNode> setRoots;

  public GenericDominatorEngine(IGraph graph) {
    this.graph = graph;
  }

  public void initialize() {
    calcIDoms();
  }

  private void orderNodes() {

    setRoots = graph.getRoots();

    for (IGraphNode node : graph.getReversePostOrderList()) {
      colOrderedIDoms.addWithKey(null, node);
    }
  }

  private static IGraphNode getCommonIDom(IGraphNode node1, IGraphNode node2, VBStyleCollection<IGraphNode, IGraphNode> orderedIDoms) {

    IGraphNode nodeOld;

    if (node1 == null) {
      return node2;
    }
    else if (node2 == null) {
      return node1;
    }

    int index1 = orderedIDoms.getIndexByKey(node1);
    int index2 = orderedIDoms.getIndexByKey(node2);

    while (index1 != index2) {
      if (index1 > index2) {
        nodeOld = node1;
        node1 = orderedIDoms.getWithKey(node1);

        if (nodeOld == node1) { // no idom - root or merging point
          return null;
        }

        index1 = orderedIDoms.getIndexByKey(node1);
      }
      else {
        nodeOld = node2;
        node2 = orderedIDoms.getWithKey(node2);

        if (nodeOld == node2) { // no idom - root or merging point
          return null;
        }

        index2 = orderedIDoms.getIndexByKey(node2);
      }
    }

    return node1;
  }

  private void calcIDoms() {

    orderNodes();

    List<IGraphNode> lstNodes = colOrderedIDoms.getLstKeys();

    while (true) {

      boolean changed = false;

      for (IGraphNode node : lstNodes) {

        IGraphNode idom = null;

        if (!setRoots.contains(node)) {
          for (IGraphNode pred : node.getPredecessors()) {
            if (colOrderedIDoms.getWithKey(pred) != null) {
              idom = getCommonIDom(idom, pred, colOrderedIDoms);
              if (idom == null) {
                break; // no idom found: merging point of two trees
              }
            }
          }
        }

        if (idom == null) {
          idom = node;
        }

        IGraphNode oldidom = colOrderedIDoms.putWithKey(idom, node);
        if (!idom.equals(oldidom)) { // oldidom is null iff the node is touched for the first time
          changed = true;
        }
      }

      if (!changed) {
        break;
      }
    }
  }

  public VBStyleCollection<IGraphNode, IGraphNode> getOrderedIDoms() {
    return colOrderedIDoms;
  }

  public boolean isDominator(IGraphNode node, IGraphNode dom) {

    while (!node.equals(dom)) {

      IGraphNode idom = colOrderedIDoms.getWithKey(node);

      if (idom == node) {
        return false; // root node or merging point
      }
      else if (idom == null) {
        throw new RuntimeException("Inconsistent idom sequence discovered!");
      }
      else {
        node = idom;
      }
    }

    return true;
  }
}
