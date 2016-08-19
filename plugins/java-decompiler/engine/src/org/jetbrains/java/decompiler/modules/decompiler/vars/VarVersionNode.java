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

  public VarVersionPair getVarPaar() {
    return new VarVersionPair(var, version);
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
