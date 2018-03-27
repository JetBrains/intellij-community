/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StrongConnectivityHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.Map.Entry;

public class Statement implements IMatchable {
  public static final int STATEDGE_ALL = 0x80000000;
  public static final int STATEDGE_DIRECT_ALL = 0x40000000;

  public static final int DIRECTION_BACKWARD = 0;
  public static final int DIRECTION_FORWARD = 1;

  public static final int TYPE_GENERAL = 0;
  public static final int TYPE_IF = 2;
  public static final int TYPE_DO = 5;
  public static final int TYPE_SWITCH = 6;
  public static final int TYPE_TRYCATCH = 7;
  public static final int TYPE_BASICBLOCK = 8;
  //public static final int TYPE_FINALLY = 9;
  public static final int TYPE_SYNCRONIZED = 10;
  public static final int TYPE_PLACEHOLDER = 11;
  public static final int TYPE_CATCHALL = 12;
  public static final int TYPE_ROOT = 13;
  public static final int TYPE_DUMMYEXIT = 14;
  public static final int TYPE_SEQUENCE = 15;

  public static final int LASTBASICTYPE_IF = 0;
  public static final int LASTBASICTYPE_SWITCH = 1;
  public static final int LASTBASICTYPE_GENERAL = 2;


  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int type;

  public Integer id;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private final Map<Integer, List<StatEdge>> mapSuccEdges = new HashMap<>();
  private final Map<Integer, List<StatEdge>> mapPredEdges = new HashMap<>();

  private final Map<Integer, List<Statement>> mapSuccStates = new HashMap<>();
  private final Map<Integer, List<Statement>> mapPredStates = new HashMap<>();

  // statement as graph
  protected final VBStyleCollection<Statement, Integer> stats = new VBStyleCollection<>();

  protected Statement parent;

  protected Statement first;

  protected List<Exprent> exprents;

  protected final HashSet<StatEdge> labelEdges = new HashSet<>();

  protected final List<Exprent> varDefinitions = new ArrayList<>();

  // copied statement, s. deobfuscating of irreducible CFGs
  private boolean copied = false;

  // relevant for the first stage of processing only
  // set to null after initializing of the statement structure

  protected Statement post;

  protected int lastBasicType = LASTBASICTYPE_GENERAL;

  protected boolean isMonitorEnter;

  protected boolean containsMonitorExit;

  protected HashSet<Statement> continueSet = new HashSet<>();

  // *****************************************************************************
  // initializers
  // *****************************************************************************

  {
    // set statement id
    id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER);
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void clearTempInformation() {

    post = null;
    continueSet = null;

    copied = false;
    // FIXME: used in FlattenStatementsHelper.flattenStatement()! check and remove
    //lastBasicType = LASTBASICTYPE_GENERAL;
    isMonitorEnter = false;
    containsMonitorExit = false;

    processMap(mapSuccEdges);
    processMap(mapPredEdges);
    processMap(mapSuccStates);
    processMap(mapPredStates);
  }

  private static <T> void processMap(Map<Integer, List<T>> map) {
    map.remove(StatEdge.TYPE_EXCEPTION);

    List<T> lst = map.get(STATEDGE_DIRECT_ALL);
    if (lst != null) {
      map.put(STATEDGE_ALL, new ArrayList<>(lst));
    }
    else {
      map.remove(STATEDGE_ALL);
    }
  }

  public void collapseNodesToStatement(Statement stat) {

    Statement head = stat.getFirst();
    Statement post = stat.getPost();

    VBStyleCollection<Statement, Integer> setNodes = stat.getStats();

    // post edges
    if (post != null) {
      for (StatEdge edge : post.getEdges(STATEDGE_DIRECT_ALL, DIRECTION_BACKWARD)) {
        if (stat.containsStatementStrict(edge.getSource())) {
          edge.getSource().changeEdgeType(DIRECTION_FORWARD, edge, StatEdge.TYPE_BREAK);
          stat.addLabeledEdge(edge);
        }
      }
    }

    // regular head edges
    for (StatEdge prededge : head.getAllPredecessorEdges()) {

      if (prededge.getType() != StatEdge.TYPE_EXCEPTION &&
          stat.containsStatementStrict(prededge.getSource())) {
        prededge.getSource().changeEdgeType(DIRECTION_FORWARD, prededge, StatEdge.TYPE_CONTINUE);
        stat.addLabeledEdge(prededge);
      }

      head.removePredecessor(prededge);
      prededge.getSource().changeEdgeNode(DIRECTION_FORWARD, prededge, stat);
      stat.addPredecessor(prededge);
    }

    if (setNodes.containsKey(first.id)) {
      first = stat;
    }

    // exception edges
    Set<Statement> setHandlers = new HashSet<>(head.getNeighbours(StatEdge.TYPE_EXCEPTION, DIRECTION_FORWARD));
    for (Statement node : setNodes) {
      setHandlers.retainAll(node.getNeighbours(StatEdge.TYPE_EXCEPTION, DIRECTION_FORWARD));
    }

    if (!setHandlers.isEmpty()) {

      for (StatEdge edge : head.getEdges(StatEdge.TYPE_EXCEPTION, DIRECTION_FORWARD)) {
        Statement handler = edge.getDestination();

        if (setHandlers.contains(handler)) {
          if (!setNodes.containsKey(handler.id)) {
            stat.addSuccessor(new StatEdge(stat, handler, edge.getExceptions()));
          }
        }
      }

      for (Statement node : setNodes) {
        for (StatEdge edge : node.getEdges(StatEdge.TYPE_EXCEPTION, DIRECTION_FORWARD)) {
          if (setHandlers.contains(edge.getDestination())) {
            node.removeSuccessor(edge);
          }
        }
      }
    }

    if (post != null &&
        !stat.getNeighbours(StatEdge.TYPE_EXCEPTION, DIRECTION_FORWARD).contains(post)) { // TODO: second condition redundant?
      stat.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, stat, post));
    }


    // adjust statement collection
    for (Statement st : setNodes) {
      stats.removeWithKey(st.id);
    }

    stats.addWithKey(stat, stat.id);

    stat.setAllParent();
    stat.setParent(this);

    stat.buildContinueSet();
    // monitorenter and monitorexit
    stat.buildMonitorFlags();

    if (stat.type == TYPE_SWITCH) {
      // special case switch, sorting leaf nodes
      ((SwitchStatement)stat).sortEdgesAndNodes();
    }
  }

  public void setAllParent() {
    for (Statement st : stats) {
      st.setParent(this);
    }
  }

  public void addLabeledEdge(StatEdge edge) {

    if (edge.closure != null) {
      edge.closure.getLabelEdges().remove(edge);
    }
    edge.closure = this;
    this.getLabelEdges().add(edge);
  }

  private void addEdgeDirectInternal(int direction, StatEdge edge, int edgetype) {
    Map<Integer, List<StatEdge>> mapEdges = direction == DIRECTION_BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<Integer, List<Statement>> mapStates = direction == DIRECTION_BACKWARD ? mapPredStates : mapSuccStates;

    mapEdges.computeIfAbsent(edgetype, k -> new ArrayList<>()).add(edge);

    mapStates.computeIfAbsent(edgetype, k -> new ArrayList<>()).add(direction == DIRECTION_BACKWARD ? edge.getSource() : edge.getDestination());
  }

  private void addEdgeInternal(int direction, StatEdge edge) {
    int type = edge.getType();

    int[] arrtypes;
    if (type == StatEdge.TYPE_EXCEPTION) {
      arrtypes = new int[]{STATEDGE_ALL, StatEdge.TYPE_EXCEPTION};
    }
    else {
      arrtypes = new int[]{STATEDGE_ALL, STATEDGE_DIRECT_ALL, type};
    }

    for (int edgetype : arrtypes) {
      addEdgeDirectInternal(direction, edge, edgetype);
    }
  }

  private void removeEdgeDirectInternal(int direction, StatEdge edge, int edgetype) {

    Map<Integer, List<StatEdge>> mapEdges = direction == DIRECTION_BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<Integer, List<Statement>> mapStates = direction == DIRECTION_BACKWARD ? mapPredStates : mapSuccStates;

    List<StatEdge> lst = mapEdges.get(edgetype);
    if (lst != null) {
      int index = lst.indexOf(edge);
      if (index >= 0) {
        lst.remove(index);
        mapStates.get(edgetype).remove(index);
      }
    }
  }

  private void removeEdgeInternal(int direction, StatEdge edge) {

    int type = edge.getType();

    int[] arrtypes;
    if (type == StatEdge.TYPE_EXCEPTION) {
      arrtypes = new int[]{STATEDGE_ALL, StatEdge.TYPE_EXCEPTION};
    }
    else {
      arrtypes = new int[]{STATEDGE_ALL, STATEDGE_DIRECT_ALL, type};
    }

    for (int edgetype : arrtypes) {
      removeEdgeDirectInternal(direction, edge, edgetype);
    }
  }

  public void addPredecessor(StatEdge edge) {
    addEdgeInternal(DIRECTION_BACKWARD, edge);
  }

  public void removePredecessor(StatEdge edge) {

    if (edge == null) {  // FIXME: redundant?
      return;
    }

    removeEdgeInternal(DIRECTION_BACKWARD, edge);
  }

  public void addSuccessor(StatEdge edge) {
    addEdgeInternal(DIRECTION_FORWARD, edge);

    if (edge.closure != null) {
      edge.closure.getLabelEdges().add(edge);
    }

    edge.getDestination().addPredecessor(edge);
  }

  public void removeSuccessor(StatEdge edge) {

    if (edge == null) {
      return;
    }

    removeEdgeInternal(DIRECTION_FORWARD, edge);

    if (edge.closure != null) {
      edge.closure.getLabelEdges().remove(edge);
    }

    if (edge.getDestination() != null) {  // TODO: redundant?
      edge.getDestination().removePredecessor(edge);
    }
  }

  // TODO: make obsolete and remove
  public void removeAllSuccessors(Statement stat) {

    if (stat == null) {
      return;
    }

    for (StatEdge edge : getAllSuccessorEdges()) {
      if (edge.getDestination() == stat) {
        removeSuccessor(edge);
      }
    }
  }

  public HashSet<Statement> buildContinueSet() {
    continueSet.clear();

    for (Statement st : stats) {
      continueSet.addAll(st.buildContinueSet());
      if (st != first) {
        continueSet.remove(st.getBasichead());
      }
    }

    for (StatEdge edge : getEdges(StatEdge.TYPE_CONTINUE, DIRECTION_FORWARD)) {
      continueSet.add(edge.getDestination().getBasichead());
    }

    if (type == TYPE_DO) {
      continueSet.remove(first.getBasichead());
    }

    return continueSet;
  }

  public void buildMonitorFlags() {

    for (Statement st : stats) {
      st.buildMonitorFlags();
    }

    switch (type) {
      case TYPE_BASICBLOCK:
        BasicBlockStatement bblock = (BasicBlockStatement)this;
        InstructionSequence seq = bblock.getBlock().getSeq();

        if (seq != null && seq.length() > 0) {
          for (int i = 0; i < seq.length(); i++) {
            if (seq.getInstr(i).opcode == CodeConstants.opc_monitorexit) {
              containsMonitorExit = true;
              break;
            }
          }
          isMonitorEnter = (seq.getLastInstr().opcode == CodeConstants.opc_monitorenter);
        }
        break;
      case TYPE_SEQUENCE:
      case TYPE_IF:
        containsMonitorExit = false;
        for (Statement st : stats) {
          containsMonitorExit |= st.isContainsMonitorExit();
        }

        break;
      case TYPE_SYNCRONIZED:
      case TYPE_ROOT:
      case TYPE_GENERAL:
        break;
      default:
        containsMonitorExit = false;
        for (Statement st : stats) {
          containsMonitorExit |= st.isContainsMonitorExit();
        }
    }
  }


  public List<Statement> getReversePostOrderList() {
    return getReversePostOrderList(first);
  }

  public List<Statement> getReversePostOrderList(Statement stat) {
    List<Statement> res = new ArrayList<>();

    addToReversePostOrderListIterative(stat, res);

    return res;
  }

  public List<Statement> getPostReversePostOrderList() {
    return getPostReversePostOrderList(null);
  }

  public List<Statement> getPostReversePostOrderList(List<Statement> lstexits) {

    List<Statement> res = new ArrayList<>();

    if (lstexits == null) {
      StrongConnectivityHelper schelper = new StrongConnectivityHelper(this);
      lstexits = StrongConnectivityHelper.getExitReps(schelper.getComponents());
    }

    HashSet<Statement> setVisited = new HashSet<>();

    for (Statement exit : lstexits) {
      addToPostReversePostOrderList(exit, res, setVisited);
    }

    if (res.size() != stats.size()) {
      throw new RuntimeException("computing post reverse post order failed!");
    }

    return res;
  }

  public boolean containsStatement(Statement stat) {
    return this == stat || containsStatementStrict(stat);
  }

  public boolean containsStatementStrict(Statement stat) {
    if (stats.contains(stat)) {
      return true;
    }

    for (Statement st : stats) {
      if (st.containsStatementStrict(stat)) {
        return true;
      }
    }

    return false;
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    throw new RuntimeException("not implemented");
  }

  // TODO: make obsolete and remove
  public List<Object> getSequentialObjects() {
    return new ArrayList<>(stats);
  }

  public void initExprents() {
    // do nothing
  }

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    // do nothing
  }

  public Statement getSimpleCopy() {
    throw new RuntimeException("not implemented");
  }

  public void initSimpleCopy() {
    if (!stats.isEmpty()) {
      first = stats.get(0);
    }
  }

  public void replaceStatement(Statement oldstat, Statement newstat) {

    for (StatEdge edge : oldstat.getAllPredecessorEdges()) {
      oldstat.removePredecessor(edge);
      edge.getSource().changeEdgeNode(DIRECTION_FORWARD, edge, newstat);
      newstat.addPredecessor(edge);
    }

    for (StatEdge edge : oldstat.getAllSuccessorEdges()) {
      oldstat.removeSuccessor(edge);
      edge.setSource(newstat);
      newstat.addSuccessor(edge);
    }

    int statindex = stats.getIndexByKey(oldstat.id);
    stats.removeWithKey(oldstat.id);
    stats.addWithKeyAndIndex(statindex, newstat, newstat.id);

    newstat.setParent(this);
    newstat.post = oldstat.post;

    if (first == oldstat) {
      first = newstat;
    }

    List<StatEdge> lst = new ArrayList<>(oldstat.getLabelEdges());

    for (int i = lst.size() - 1; i >= 0; i--) {
      StatEdge edge = lst.get(i);
      if (edge.getSource() != newstat) {
        newstat.addLabeledEdge(edge);
      }
      else {
        if (this == edge.getDestination() || this.containsStatementStrict(edge.getDestination())) {
          edge.closure = null;
        }
        else {
          this.addLabeledEdge(edge);
        }
      }
    }

    oldstat.getLabelEdges().clear();
  }


  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private static void addToReversePostOrderListIterative(Statement root, List<Statement> lst) {

    LinkedList<Statement> stackNode = new LinkedList<>();
    LinkedList<Integer> stackIndex = new LinkedList<>();
    HashSet<Statement> setVisited = new HashSet<>();

    stackNode.add(root);
    stackIndex.add(0);

    while (!stackNode.isEmpty()) {

      Statement node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<StatEdge> lstEdges = node.getAllSuccessorEdges();

      for (; index < lstEdges.size(); index++) {
        StatEdge edge = lstEdges.get(index);
        Statement succ = edge.getDestination();

        if (!setVisited.contains(succ) &&
            (edge.getType() == StatEdge.TYPE_REGULAR || edge.getType() == StatEdge.TYPE_EXCEPTION)) { // TODO: edge filter?

          stackIndex.add(index + 1);

          stackNode.add(succ);
          stackIndex.add(0);

          break;
        }
      }

      if (index == lstEdges.size()) {
        lst.add(0, node);

        stackNode.removeLast();
      }
    }
  }


  private static void addToPostReversePostOrderList(Statement stat, List<Statement> lst, HashSet<Statement> setVisited) {

    if (setVisited.contains(stat)) { // because of not considered exception edges, s. isExitComponent. Should be rewritten, if possible.
      return;
    }
    setVisited.add(stat);

    for (StatEdge prededge : stat.getEdges(StatEdge.TYPE_REGULAR | StatEdge.TYPE_EXCEPTION, DIRECTION_BACKWARD)) {
      Statement pred = prededge.getSource();
      if (!setVisited.contains(pred)) {
        addToPostReversePostOrderList(pred, lst, setVisited);
      }
    }

    lst.add(0, stat);
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public void changeEdgeNode(int direction, StatEdge edge, Statement value) {

    Map<Integer, List<StatEdge>> mapEdges = direction == DIRECTION_BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<Integer, List<Statement>> mapStates = direction == DIRECTION_BACKWARD ? mapPredStates : mapSuccStates;

    int type = edge.getType();

    int[] arrtypes;
    if (type == StatEdge.TYPE_EXCEPTION) {
      arrtypes = new int[]{STATEDGE_ALL, StatEdge.TYPE_EXCEPTION};
    }
    else {
      arrtypes = new int[]{STATEDGE_ALL, STATEDGE_DIRECT_ALL, type};
    }

    for (int edgetype : arrtypes) {
      List<StatEdge> lst = mapEdges.get(edgetype);
      if (lst != null) {
        int index = lst.indexOf(edge);
        if (index >= 0) {
          mapStates.get(edgetype).set(index, value);
        }
      }
    }

    if (direction == DIRECTION_BACKWARD) {
      edge.setSource(value);
    }
    else {
      edge.setDestination(value);
    }
  }

  public void changeEdgeType(int direction, StatEdge edge, int newtype) {

    int oldtype = edge.getType();
    if (oldtype == newtype) {
      return;
    }

    if (oldtype == StatEdge.TYPE_EXCEPTION || newtype == StatEdge.TYPE_EXCEPTION) {
      throw new RuntimeException("Invalid edge type!");
    }

    removeEdgeDirectInternal(direction, edge, oldtype);
    addEdgeDirectInternal(direction, edge, newtype);

    if (direction == DIRECTION_FORWARD) {
      edge.getDestination().changeEdgeType(DIRECTION_BACKWARD, edge, newtype);
    }

    edge.setType(newtype);
  }


  private List<StatEdge> getEdges(int type, int direction) {

    Map<Integer, List<StatEdge>> map = direction == DIRECTION_BACKWARD ? mapPredEdges : mapSuccEdges;

    List<StatEdge> res;
    if ((type & (type - 1)) == 0) {
      res = map.get(type);
      res = res == null ? new ArrayList<>() : new ArrayList<>(res);
    }
    else {
      res = new ArrayList<>();
      for (int edgetype : StatEdge.TYPES) {
        if ((type & edgetype) != 0) {
          List<StatEdge> lst = map.get(edgetype);
          if (lst != null) {
            res.addAll(lst);
          }
        }
      }
    }

    return res;
  }

  public List<Statement> getNeighbours(int type, int direction) {

    Map<Integer, List<Statement>> map = direction == DIRECTION_BACKWARD ? mapPredStates : mapSuccStates;

    List<Statement> res;
    if ((type & (type - 1)) == 0) {
      res = map.get(type);
      res = res == null ? new ArrayList<>() : new ArrayList<>(res);
    }
    else {
      res = new ArrayList<>();
      for (int edgetype : StatEdge.TYPES) {
        if ((type & edgetype) != 0) {
          List<Statement> lst = map.get(edgetype);
          if (lst != null) {
            res.addAll(lst);
          }
        }
      }
    }

    return res;
  }

  public Set<Statement> getNeighboursSet(int type, int direction) {
    return new HashSet<>(getNeighbours(type, direction));
  }

  public List<StatEdge> getSuccessorEdges(int type) {
    return getEdges(type, DIRECTION_FORWARD);
  }

  public List<StatEdge> getPredecessorEdges(int type) {
    return getEdges(type, DIRECTION_BACKWARD);
  }

  public List<StatEdge> getAllSuccessorEdges() {
    return getEdges(STATEDGE_ALL, DIRECTION_FORWARD);
  }

  public List<StatEdge> getAllPredecessorEdges() {
    return getEdges(STATEDGE_ALL, DIRECTION_BACKWARD);
  }

  public Statement getFirst() {
    return first;
  }

  public void setFirst(Statement first) {
    this.first = first;
  }

  public Statement getPost() {
    return post;
  }

  public VBStyleCollection<Statement, Integer> getStats() {
    return stats;
  }

  public int getLastBasicType() {
    return lastBasicType;
  }

  public HashSet<Statement> getContinueSet() {
    return continueSet;
  }

  public boolean isContainsMonitorExit() {
    return containsMonitorExit;
  }

  public boolean isMonitorEnter() {
    return isMonitorEnter;
  }

  public BasicBlockStatement getBasichead() {
    if (type == TYPE_BASICBLOCK) {
      return (BasicBlockStatement)this;
    }
    else {
      return first.getBasichead();
    }
  }

  public boolean isLabeled() {

    for (StatEdge edge : labelEdges) {
      if (edge.labeled && edge.explicit) {  // FIXME: consistent setting
        return true;
      }
    }
    return false;
  }

  public boolean hasBasicSuccEdge() {

    // FIXME: default switch

    return type == TYPE_BASICBLOCK || (type == TYPE_IF &&
                                                        ((IfStatement)this).iftype == IfStatement.IFTYPE_IF) ||
                  (type == TYPE_DO && ((DoStatement)this).getLooptype() != DoStatement.LOOP_DO);
  }


  public Statement getParent() {
    return parent;
  }

  public void setParent(Statement parent) {
    this.parent = parent;
  }

  public HashSet<StatEdge> getLabelEdges() {  // FIXME: why HashSet?
    return labelEdges;
  }

  public List<Exprent> getVarDefinitions() {
    return varDefinitions;
  }

  public List<Exprent> getExprents() {
    return exprents;
  }

  public void setExprents(List<Exprent> exprents) {
    this.exprents = exprents;
  }

  public boolean isCopied() {
    return copied;
  }

  public void setCopied(boolean copied) {
    this.copied = copied;
  }

  // helper methods
  public String toString() {
    return id.toString();
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public IMatchable findObject(MatchNode matchNode, int index) {
    int node_type = matchNode.getType();

    if (node_type == MatchNode.MATCHNODE_STATEMENT && !this.stats.isEmpty()) {
      String position = (String)matchNode.getRuleValue(MatchProperties.STATEMENT_POSITION);
      if (position != null) {
        if (position.matches("-?\\d+")) {
          return this.stats.get((this.stats.size() + Integer.parseInt(position)) % this.stats.size()); // care for negative positions
        }
      }
      else if (index < this.stats.size()) { // use 'index' parameter
        return this.stats.get(index);
      }
    }
    else if (node_type == MatchNode.MATCHNODE_EXPRENT && this.exprents != null && !this.exprents.isEmpty()) {
      String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
      if (position != null) {
        if (position.matches("-?\\d+")) {
          return this.exprents.get((this.exprents.size() + Integer.parseInt(position)) % this.exprents.size()); // care for negative positions
        }
      }
      else if (index < this.exprents.size()) { // use 'index' parameter
        return this.exprents.get(index);
      }
    }

    return null;
  }

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (matchNode.getType() != MatchNode.MATCHNODE_STATEMENT) {
      return false;
    }

    for (Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      switch (rule.getKey()) {
        case STATEMENT_TYPE:
          if (this.type != (Integer)rule.getValue().value) {
            return false;
          }
          break;
        case STATEMENT_STATSIZE:
          if (this.stats.size() != (Integer)rule.getValue().value) {
            return false;
          }
          break;
        case STATEMENT_EXPRSIZE:
          int exprsize = (Integer)rule.getValue().value;
          if (exprsize == -1) {
            if (this.exprents != null) {
              return false;
            }
          }
          else {
            if (this.exprents == null || this.exprents.size() != exprsize) {
              return false;
            }
          }
          break;
        case STATEMENT_RET:
          if (!engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
            return false;
          }
          break;
      }
    }

    return true;
  }
}