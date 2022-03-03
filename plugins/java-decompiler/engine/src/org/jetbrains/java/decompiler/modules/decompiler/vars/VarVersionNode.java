// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VarVersionNode implements IGraphNode {
  public static final int FLAG_PHANTOM_FIN_EXIT = 2;

  public final int var;
  public final int version;
  public final Set<VarVersionEdge> predecessors = new HashSet<>();
  public final Set<VarVersionEdge> successors = new HashSet<>();

  public int flags;
  public SFormsFastMapDirect live = new SFormsFastMapDirect();

  public VarVersionNode(int var, int version) {
    this.var = var;
    this.version = version;
  }

  public void addPredecessor(VarVersionEdge edge) {
    predecessors.add(edge);
  }

  public void removePredecessor(VarVersionEdge edge) {
    predecessors.remove(edge);
  }

  public void addSuccessor(VarVersionEdge edge) {
    successors.add(edge);
  }

  public void removeSuccessor(VarVersionEdge edge) {
    successors.remove(edge);
  }

  @Override
  public List<IGraphNode> getPredecessorNodes() {
    List<IGraphNode> lst = new ArrayList<>(predecessors.size());
    for (VarVersionEdge edge : predecessors) {
      lst.add(edge.source);
    }
    return lst;
  }

  @Override
  public String toString() {
    return "(" + var + '_' + version + ')';
  }
}
