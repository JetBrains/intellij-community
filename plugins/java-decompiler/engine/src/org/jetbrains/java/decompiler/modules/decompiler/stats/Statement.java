// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.StrongConnectivityHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.DoStatement.LoopType;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.Map.Entry;

public abstract class Statement implements IMatchable {
  public StatementType type;
  public int id;

  private final Map<EdgeType, List<StatEdge>> mapSuccEdges = new HashMap<>();
  private final Map<EdgeType, List<StatEdge>> mapPredEdges = new HashMap<>();

  private final Map<EdgeType, List<Statement>> mapSuccStates = new HashMap<>();
  private final Map<EdgeType, List<Statement>> mapPredStates = new HashMap<>();

  private final HashSet<StatEdge> labelEdges = new HashSet<>();
  // copied statement, s. deobfuscating of irreducible CFGs
  private boolean copied = false;
  // statement as graph
  protected final VBStyleCollection<Statement, Integer> stats = new VBStyleCollection<>();
  protected Statement parent;
  protected Statement first;
  protected List<Exprent> exprents;
  protected final List<Exprent> varDefinitions = new ArrayList<>();
  // relevant for the first stage of processing only
  // set to null after initializing of the statement structure
  protected Statement post;
  protected StatementType lastBasicType = StatementType.GENERAL;
  protected boolean isMonitorEnter;
  protected boolean containsMonitorExit;
  protected HashSet<Statement> continueSet = new HashSet<>();

  //Statement must live only in one Thread
  private final CancellationManager cancellationManager = DecompilerContext.getCancellationManager();

  {
    id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER);
  }

  Statement(@NotNull StatementType type) {
    this.type = type;
    cancellationManager.checkCanceled();
  }

  Statement(@NotNull StatementType type, int id) {
    this.type = type;
    this.id = id;
    cancellationManager.checkCanceled();
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

  private static <T> void processMap(Map<EdgeType, List<T>> map) {
    map.remove(EdgeType.EXCEPTION);

    List<T> lst = map.get(EdgeType.DIRECT_ALL);
    if (lst != null) {
      map.put(EdgeType.ALL, new ArrayList<>(lst));
    }
    else {
      map.remove(EdgeType.ALL);
    }
  }

  public void collapseNodesToStatement(Statement stat) {
    cancellationManager.checkCanceled();
    Statement head = stat.getFirst();
    Statement post = stat.getPost();

    VBStyleCollection<Statement, Integer> setNodes = stat.getStats();

    // post edges
    if (post != null) {
      for (StatEdge edge : post.getEdges(EdgeType.DIRECT_ALL, EdgeDirection.BACKWARD)) {
        if (stat.containsStatementStrict(edge.getSource())) {
          edge.getSource().changeEdgeType(EdgeDirection.FORWARD, edge, EdgeType.BREAK);
          stat.addLabeledEdge(edge);
        }
      }
    }

    // regular head edges
    for (StatEdge prededge : head.getAllPredecessorEdges()) {
      cancellationManager.checkCanceled();

      if (prededge.getType() != EdgeType.EXCEPTION &&
          stat.containsStatementStrict(prededge.getSource())) {
        prededge.getSource().changeEdgeType(EdgeDirection.FORWARD, prededge, EdgeType.CONTINUE);
        stat.addLabeledEdge(prededge);
      }

      head.removePredecessor(prededge);
      prededge.getSource().changeEdgeNode(EdgeDirection.FORWARD, prededge, stat);
      stat.addPredecessor(prededge);
    }

    if (setNodes.containsKey(first.id)) {
      first = stat;
    }

    // exception edges
    Set<Statement> setHandlers = new HashSet<>(head.getNeighbours(EdgeType.EXCEPTION, EdgeDirection.FORWARD));
    for (Statement node : setNodes) {
      setHandlers.retainAll(node.getNeighbours(EdgeType.EXCEPTION, EdgeDirection.FORWARD));
    }

    if (!setHandlers.isEmpty()) {

      for (StatEdge edge : head.getEdges(EdgeType.EXCEPTION, EdgeDirection.FORWARD)) {
        Statement handler = edge.getDestination();

        if (setHandlers.contains(handler)) {
          if (!setNodes.containsKey(handler.id)) {
            stat.addSuccessor(new StatEdge(stat, handler, edge.getExceptions()));
          }
        }
      }

      for (Statement node : setNodes) {
        for (StatEdge edge : node.getEdges(EdgeType.EXCEPTION, EdgeDirection.FORWARD)) {
          if (setHandlers.contains(edge.getDestination())) {
            node.removeSuccessor(edge);
          }
        }
      }
    }

    if (post != null &&
        !stat.getNeighbours(EdgeType.EXCEPTION, EdgeDirection.FORWARD).contains(post)) { // TODO: second condition redundant?
      stat.addSuccessor(new StatEdge(EdgeType.REGULAR, stat, post));
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

    if (stat.type == StatementType.SWITCH) {
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

  private void addEdgeDirectInternal(EdgeDirection direction, StatEdge edge, EdgeType edgetype) {
    Map<EdgeType, List<StatEdge>> mapEdges = direction == EdgeDirection.BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<EdgeType, List<Statement>> mapStates = direction == EdgeDirection.BACKWARD ? mapPredStates : mapSuccStates;

    mapEdges.computeIfAbsent(edgetype, k -> new ArrayList<>()).add(edge);

    mapStates.computeIfAbsent(edgetype, k -> new ArrayList<>()).add(direction == EdgeDirection.BACKWARD ? edge.getSource() : edge.getDestination());
  }

  private void addEdgeInternal(EdgeDirection direction, StatEdge edge) {
    EdgeType type = edge.getType();

    EdgeType[] arrtypes;
    if (type == EdgeType.EXCEPTION) {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.EXCEPTION};
    }
    else {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.DIRECT_ALL, type};
    }

    for (EdgeType edgetype : arrtypes) {
      addEdgeDirectInternal(direction, edge, edgetype);
    }
  }

  private void removeEdgeDirectInternal(EdgeDirection direction, StatEdge edge, EdgeType edgetype) {

    Map<EdgeType, List<StatEdge>> mapEdges = direction == EdgeDirection.BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<EdgeType, List<Statement>> mapStates = direction == EdgeDirection.BACKWARD ? mapPredStates : mapSuccStates;

    List<StatEdge> lst = mapEdges.get(edgetype);
    if (lst != null) {
      int index = lst.indexOf(edge);
      if (index >= 0) {
        lst.remove(index);
        mapStates.get(edgetype).remove(index);
      }
    }
  }

  private void removeEdgeInternal(EdgeDirection direction, StatEdge edge) {

    EdgeType type = edge.getType();

    EdgeType[] arrtypes;
    if (type == EdgeType.EXCEPTION) {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.EXCEPTION};
    }
    else {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.DIRECT_ALL, type};
    }

    for (EdgeType edgetype : arrtypes) {
      removeEdgeDirectInternal(direction, edge, edgetype);
    }
  }

  public void addPredecessor(StatEdge edge) {
    addEdgeInternal(EdgeDirection.BACKWARD, edge);
  }

  public void removePredecessor(StatEdge edge) {

    if (edge == null) {  // FIXME: redundant?
      return;
    }

    removeEdgeInternal(EdgeDirection.BACKWARD, edge);
  }

  public void addSuccessor(StatEdge edge) {
    addEdgeInternal(EdgeDirection.FORWARD, edge);

    if (edge.closure != null) {
      edge.closure.getLabelEdges().add(edge);
    }

    edge.getDestination().addPredecessor(edge);
  }

  public void removeSuccessor(StatEdge edge) {

    if (edge == null) {
      return;
    }

    removeEdgeInternal(EdgeDirection.FORWARD, edge);

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
    cancellationManager.checkCanceled();
    continueSet.clear();

    for (Statement st : stats) {
      continueSet.addAll(st.buildContinueSet());
      if (st != first) {
        continueSet.remove(st.getBasichead());
      }
    }

    for (StatEdge edge : getEdges(EdgeType.CONTINUE, EdgeDirection.FORWARD)) {
      continueSet.add(edge.getDestination().getBasichead());
    }

    if (type == StatementType.DO) {
      continueSet.remove(first.getBasichead());
    }

    return continueSet;
  }

  public void buildMonitorFlags() {

    for (Statement st : stats) {
      st.buildMonitorFlags();
    }

    switch (type) {
      case BASIC_BLOCK -> {
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
      }
      case SEQUENCE, IF -> {
        containsMonitorExit = false;
        for (Statement st : stats) {
          containsMonitorExit |= st.isContainsMonitorExit();
        }
      }
      case SYNCHRONIZED, ROOT, GENERAL -> { }
      default -> {
        containsMonitorExit = false;
        for (Statement st : stats) {
          containsMonitorExit |= st.isContainsMonitorExit();
        }
      }
    }
  }


  public List<Statement> getReversePostOrderList() {
    return getReversePostOrderList(first);
  }

  public List<Statement> getReversePostOrderList(Statement stat) {
    cancellationManager.checkCanceled();
    List<Statement> res = new ArrayList<>();

    addToReversePostOrderListIterative(stat, res);

    return res;
  }

  public List<Statement> getPostReversePostOrderList() {
    return getPostReversePostOrderList(null);
  }

  public List<Statement> getPostReversePostOrderList(List<Statement> lstexits) {
    cancellationManager.checkCanceled();
    List<Statement> res = new ArrayList<>();

    if (lstexits == null) {
      lstexits = new StrongConnectivityHelper(this).getExitReps();
    }

    HashSet<Statement> setVisited = new HashSet<>();

    for (Statement exit : lstexits) {
      cancellationManager.checkCanceled();
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
    cancellationManager.checkCanceled();
    for (StatEdge edge : oldstat.getAllPredecessorEdges()) {
      oldstat.removePredecessor(edge);
      edge.getSource().changeEdgeNode(EdgeDirection.FORWARD, edge, newstat);
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

  private static void addToReversePostOrderListIterative(Statement root, List<? super Statement> lst) {

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
            (edge.getType() == EdgeType.REGULAR || edge.getType() == EdgeType.EXCEPTION)) { // TODO: edge filter?

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


  private static void addToPostReversePostOrderList(Statement stat, List<? super Statement> lst, HashSet<? super Statement> setVisited) {

    if (setVisited.contains(stat)) { // because of not considered exception edges, s. isExitComponent. Should be rewritten, if possible.
      return;
    }
    setVisited.add(stat);

    for (StatEdge prededge : stat.getEdges(EdgeType.REGULAR.unite(EdgeType.EXCEPTION), EdgeDirection.BACKWARD)) {
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

  public void changeEdgeNode(EdgeDirection direction, StatEdge edge, Statement value) {

    Map<EdgeType, List<StatEdge>> mapEdges = direction == EdgeDirection.BACKWARD ? mapPredEdges : mapSuccEdges;
    Map<EdgeType, List<Statement>> mapStates = direction == EdgeDirection.BACKWARD ? mapPredStates : mapSuccStates;

    EdgeType type = edge.getType();

    EdgeType[] arrtypes;
    if (type == EdgeType.EXCEPTION) {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.EXCEPTION};
    }
    else {
      arrtypes = new EdgeType[]{EdgeType.ALL, EdgeType.DIRECT_ALL, type};
    }

    for (EdgeType edgetype : arrtypes) {
      List<StatEdge> lst = mapEdges.get(edgetype);
      if (lst != null) {
        int index = lst.indexOf(edge);
        if (index >= 0) {
          mapStates.get(edgetype).set(index, value);
        }
      }
    }

    if (direction == EdgeDirection.BACKWARD) {
      edge.setSource(value);
    }
    else {
      edge.setDestination(value);
    }
  }

  public void changeEdgeType(EdgeDirection direction, StatEdge edge, EdgeType newtype) {

    EdgeType oldtype = edge.getType();
    if (oldtype == newtype) {
      return;
    }

    if (oldtype == EdgeType.EXCEPTION || newtype == EdgeType.EXCEPTION) {
      throw new RuntimeException("Invalid edge type!");
    }

    removeEdgeDirectInternal(direction, edge, oldtype);
    addEdgeDirectInternal(direction, edge, newtype);

    if (direction == EdgeDirection.FORWARD) {
      edge.getDestination().changeEdgeType(EdgeDirection.BACKWARD, edge, newtype);
    }

    edge.setType(newtype);
  }


  private List<StatEdge> getEdges(EdgeType type, @NotNull EdgeDirection direction) {
    cancellationManager.checkCanceled();

    Map<EdgeType, List<StatEdge>> map = direction == EdgeDirection.BACKWARD ? mapPredEdges : mapSuccEdges;

    List<StatEdge> res;
    if ((type.mask() & (type.mask() - 1)) == 0) {
      res = map.get(type);
      res = res == null ? new ArrayList<>() : new ArrayList<>(res);
    }
    else {
      res = new ArrayList<>();
      for (EdgeType edgetype : EdgeType.types()) {
        if ((type.mask() & edgetype.mask()) != 0) {
          List<StatEdge> lst = map.get(edgetype);
          if (lst != null) {
            res.addAll(lst);
          }
        }
      }
    }

    return res;
  }

  public List<Statement> getNeighbours(EdgeType type, EdgeDirection direction) {

    Map<EdgeType, List<Statement>> map = direction == EdgeDirection.BACKWARD ? mapPredStates : mapSuccStates;

    List<Statement> res;
    if ((type.mask() & (type.mask() - 1)) == 0) {
      res = map.get(type);
      res = res == null ? new ArrayList<>() : new ArrayList<>(res);
    }
    else {
      res = new ArrayList<>();
      for (EdgeType edgetype : EdgeType.types()) {
        if ((type.mask() & edgetype.mask()) != 0) {
          List<Statement> lst = map.get(edgetype);
          if (lst != null) {
            res.addAll(lst);
          }
        }
      }
    }

    return res;
  }

  public Set<Statement> getNeighboursSet(EdgeType type, EdgeDirection direction) {
    return new HashSet<>(getNeighbours(type, direction));
  }

  public List<StatEdge> getSuccessorEdges(EdgeType type) {
    return getEdges(type, EdgeDirection.FORWARD);
  }

  public List<StatEdge> getPredecessorEdges(EdgeType type) {
    return getEdges(type, EdgeDirection.BACKWARD);
  }

  public List<StatEdge> getAllSuccessorEdges() {
    return getEdges(EdgeType.ALL, EdgeDirection.FORWARD);
  }

  public List<StatEdge> getAllPredecessorEdges() {
    return getEdges(EdgeType.ALL, EdgeDirection.BACKWARD);
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
    cancellationManager.checkCanceled();
    return stats;
  }

  public StatementType getLastBasicType() {
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
    if (type == StatementType.BASIC_BLOCK) {
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

    return type == StatementType.BASIC_BLOCK || (type == StatementType.IF &&
                                                        ((IfStatement)this).iftype == IfStatement.IFTYPE_IF) ||
           (type == StatementType.DO && ((DoStatement)this).getLoopType() != LoopType.DO);
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
    cancellationManager.checkCanceled();
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
    return Integer.toString(id);
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
        case STATEMENT_TYPE -> {
          if (this.type != rule.getValue().value) {
            return false;
          }
        }
        case STATEMENT_STATSIZE -> {
          if (this.stats.size() != (Integer)rule.getValue().value) {
            return false;
          }
        }
        case STATEMENT_EXPRSIZE -> {
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
        }
        case STATEMENT_RET -> {
          if (!engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
            return false;
          }
        }
      }
    }

    return true;
  }

  public enum StatementType {
    GENERAL,
    IF,
    DO,
    SWITCH,
    TRY_CATCH,
    BASIC_BLOCK,
    // FINALLY,
    SYNCHRONIZED,
    PLACEHOLDER,
    CATCH_ALL,
    ROOT,
    DUMMY_EXIT,
    SEQUENCE
  }
}