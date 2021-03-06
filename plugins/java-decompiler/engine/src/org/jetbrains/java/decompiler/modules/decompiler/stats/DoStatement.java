// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.List;


public final class DoStatement extends Statement {

  public static final int LOOP_DO = 0;
  public static final int LOOP_DOWHILE = 1;
  public static final int LOOP_WHILE = 2;
  public static final int LOOP_FOR = 3;

  private int looptype;

  private final List<Exprent> initExprent = new ArrayList<>();
  private final List<Exprent> conditionExprent = new ArrayList<>();
  private final List<Exprent> incExprent = new ArrayList<>();

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private DoStatement() {
    type = Statement.TYPE_DO;
    looptype = LOOP_DO;

    initExprent.add(null);
    conditionExprent.add(null);
    incExprent.add(null);
  }

  private DoStatement(Statement head) {

    this();

    first = head;
    stats.addWithKey(first, first.id);

    // post is always null!
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead(Statement head) {

    if (head.getLastBasicType() == LASTBASICTYPE_GENERAL && !head.isMonitorEnter()) {

      // at most one outgoing edge
      StatEdge edge = null;
      List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
      if (!lstSuccs.isEmpty()) {
        edge = lstSuccs.get(0);
      }

      // regular loop
      if (edge != null && edge.getType() == StatEdge.TYPE_REGULAR && edge.getDestination() == head) {
        return new DoStatement(head);
      }

      // continues
      if (head.type != TYPE_DO && (edge == null || edge.getType() != StatEdge.TYPE_REGULAR) &&
          head.getContinueSet().contains(head.getBasichead())) {
        return new DoStatement(head);
      }
    }

    return null;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));

    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(this.id.toString()).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    switch (looptype) {
      case LOOP_DO:
        buf.appendIndent(indent).append("while(true) {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case LOOP_DOWHILE:
        buf.appendIndent(indent).append("do {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("} while(").append(conditionExprent.get(0).toJava(indent, tracer)).append(");").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case LOOP_WHILE:
        buf.appendIndent(indent).append("while(").append(conditionExprent.get(0).toJava(indent, tracer)).append(") {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case LOOP_FOR:
        buf.appendIndent(indent).append("for(");
        if (initExprent.get(0) != null) {
          buf.append(initExprent.get(0).toJava(indent, tracer));
        }
        buf.append("; ")
          .append(conditionExprent.get(0).toJava(indent, tracer)).append("; ").append(incExprent.get(0).toJava(indent, tracer)).append(") {")
          .appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
    }

    return buf;
  }

  @Override
  public List<Object> getSequentialObjects() {

    List<Object> lst = new ArrayList<>();

    switch (looptype) {
      case LOOP_FOR:
        if (getInitExprent() != null) {
          lst.add(getInitExprent());
        }
      case LOOP_WHILE:
        lst.add(getConditionExprent());
    }

    lst.add(first);

    switch (looptype) {
      case LOOP_DOWHILE:
        lst.add(getConditionExprent());
        break;
      case LOOP_FOR:
        lst.add(getIncExprent());
    }

    return lst;
  }

  @Override
  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (initExprent.get(0) == oldexpr) {
      initExprent.set(0, newexpr);
    }
    if (conditionExprent.get(0) == oldexpr) {
      conditionExprent.set(0, newexpr);
    }
    if (incExprent.get(0) == oldexpr) {
      incExprent.set(0, newexpr);
    }
  }

  @Override
  public Statement getSimpleCopy() {
    return new DoStatement();
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public List<Exprent> getInitExprentList() {
    return initExprent;
  }

  public List<Exprent> getConditionExprentList() {
    return conditionExprent;
  }

  public List<Exprent> getIncExprentList() {
    return incExprent;
  }

  public Exprent getConditionExprent() {
    return conditionExprent.get(0);
  }

  public void setConditionExprent(Exprent conditionExprent) {
    this.conditionExprent.set(0, conditionExprent);
  }

  public Exprent getIncExprent() {
    return incExprent.get(0);
  }

  public void setIncExprent(Exprent incExprent) {
    this.incExprent.set(0, incExprent);
  }

  public Exprent getInitExprent() {
    return initExprent.get(0);
  }

  public void setInitExprent(Exprent initExprent) {
    this.initExprent.set(0, initExprent);
  }

  public int getLooptype() {
    return looptype;
  }

  public void setLooptype(int looptype) {
    this.looptype = looptype;
  }
}
