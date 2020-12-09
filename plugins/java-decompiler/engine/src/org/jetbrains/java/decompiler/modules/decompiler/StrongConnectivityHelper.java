// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.*;

public class StrongConnectivityHelper {
  private final List<List<Statement>> components;
  private final Set<Statement> setProcessed;

  private ListStack<Statement> lstack;
  private int ncounter;
  private Set<Statement> tset;
  private Map<Statement, Integer> dfsnummap;
  private Map<Statement, Integer> lowmap;

  public StrongConnectivityHelper(Statement stat) {
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
  }

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

    for (Statement succ : lstSuccs) {
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

  public static boolean isExitComponent(List<? extends Statement> lst) {
    Set<Statement> set = new HashSet<>();
    for (Statement stat : lst) {
      set.addAll(stat.getNeighbours(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD));
    }
    for (Statement stat : lst) {
      set.remove(stat);
    }

    return (set.size() == 0);
  }

  public static List<Statement> getExitReps(List<? extends List<Statement>> lst) {
    List<Statement> res = new ArrayList<>();

    for (List<Statement> comp : lst) {
      if (isExitComponent(comp)) {
        res.add(comp.get(0));
      }
    }

    return res;
  }

  public List<List<Statement>> getComponents() {
    return components;
  }
}