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
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

//  --------------------------------------------------------------------
//    Algorithm
//  -------------------------------------------------------------------- 
//  DFS(G)
//  {
//  make a new vertex x with edges x->v for all v
//  initialize a counter N to zero
//  initialize list L to empty
//  build directed tree T, initially a single vertex {x}
//  visit(x)
//  }
//
//  visit(p)
//  {
//  add p to L
//  dfsnum(p) = N
//  increment N
//  low(p) = dfsnum(p)
//  for each edge p->q
//      if q is not already in T
//      {
//      add p->q to T
//      visit(q)
//      low(p) = min(low(p), low(q))
//      } else low(p) = min(low(p), dfsnum(q))
//
//  if low(p)=dfsnum(p)
//  {
//      output "component:"
//      repeat
//      remove last element v from L
//      output v
//      remove v from G
//      until v=p
//  }
//  }	
//  -------------------------------------------------------------------- 

public class StrongConnectivityHelper {

  private ListStack<Statement> lstack;

  private int ncounter;

  private HashSet<Statement> tset;
  private HashMap<Statement, Integer> dfsnummap;
  private HashMap<Statement, Integer> lowmap;

  private List<List<Statement>> components;

  private HashSet<Statement> setProcessed;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public StrongConnectivityHelper() {
  }

  public StrongConnectivityHelper(Statement stat) {
    findComponents(stat);
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public List<List<Statement>> findComponents(Statement stat) {

    components = new ArrayList<>();
    setProcessed = new HashSet<>();

    visitTree(stat.getFirst());

    for (Statement st : stat.getStats()) {
      if (!setProcessed.contains(st) && st.getPredecessorEdges(Statement.STATEDGE_DIRECT_ALL).isEmpty()) {
        visitTree(st);
      }
    }

    // should not find any more nodes! FIXME: ??
    for (Statement st : stat.getStats()) {
      if (!setProcessed.contains(st)) {
        visitTree(st);
      }
    }

    return components;
  }

  public static boolean isExitComponent(List<Statement> lst) {

    HashSet<Statement> set = new HashSet<>();
    for (Statement stat : lst) {
      set.addAll(stat.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD));
    }
    set.removeAll(lst);

    return (set.size() == 0);
  }

  public static List<Statement> getExitReps(List<List<Statement>> lst) {

    List<Statement> res = new ArrayList<>();

    for (List<Statement> comp : lst) {
      if (isExitComponent(comp)) {
        res.add(comp.get(0));
      }
    }

    return res;
  }

  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private void visitTree(Statement stat) {
    lstack = new ListStack<>();
    ncounter = 0;
    tset = new HashSet<>();
    dfsnummap = new HashMap<>();
    lowmap = new HashMap<>();

    visit(stat);

    setProcessed.addAll(tset);
    setProcessed.add(stat);
  }

  private void visit(Statement stat) {

    lstack.push(stat);
    dfsnummap.put(stat, ncounter);
    lowmap.put(stat, ncounter);
    ncounter++;

    List<Statement> lstSuccs = stat.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD); // TODO: set?
    lstSuccs.removeAll(setProcessed);

    for (int i = 0; i < lstSuccs.size(); i++) {
      Statement succ = lstSuccs.get(i);
      int secvalue;

      if (tset.contains(succ)) {
        secvalue = dfsnummap.get(succ);
      }
      else {
        tset.add(succ);
        visit(succ);
        secvalue = lowmap.get(succ);
      }
      lowmap.put(stat, Math.min(lowmap.get(stat), secvalue));
    }


    if (lowmap.get(stat).intValue() == dfsnummap.get(stat).intValue()) {
      List<Statement> lst = new ArrayList<>();
      Statement v;
      do {
        v = lstack.pop();
        lst.add(v);
      }
      while (v != stat);
      components.add(lst);
    }
  }


  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public List<List<Statement>> getComponents() {
    return components;
  }

  public void setComponents(List<List<Statement>> components) {
    this.components = components;
  }
}
