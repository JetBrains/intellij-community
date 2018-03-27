// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VarVersionNode implements IGraphNode {

  public static final int FLAG_PHANTOM_FINEXIT = 2;

  public final int var;

  public final int version;

  public final Set<VarVersionEdge> succs = new HashSet<>();

  public final Set<VarVersionEdge> preds = new HashSet<>();

  public int flags;

  public SFormsFastMapDirect live = new SFormsFastMapDirect();


  public VarVersionNode(int var, int version) {
    this.var = var;
    this.version = version;
  }

  public List<IGraphNode> getPredecessors() {
    List<IGraphNode> lst = new ArrayList<>(preds.size());
    for (VarVersionEdge edge : preds) {
      lst.add(edge.source);
    }
    return lst;
  }

  public void removeSuccessor(VarVersionEdge edge) {
    succs.remove(edge);
  }

  public void removePredecessor(VarVersionEdge edge) {
    preds.remove(edge);
  }

  public void addSuccessor(VarVersionEdge edge) {
    succs.add(edge);
  }

  public void addPredecessor(VarVersionEdge edge) {
    preds.add(edge);
  }

  @Override
  public String toString() {
    return "(" + var + "_" + version + ")";
  }
}