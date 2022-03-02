// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DoStatement extends Statement {
  private final List<@Nullable Exprent> initExprent = new ArrayList<>();
  private final List<@Nullable Exprent> conditionExprent = new ArrayList<>();
  private final List<@Nullable Exprent> incExprent = new ArrayList<>();

  private @NotNull LoopType loopType;

  private DoStatement() {
    super(StatementType.DO);
    initExprent.add(null);
    conditionExprent.add(null);
    incExprent.add(null);
    loopType = LoopType.DO;
  }

  private DoStatement(Statement head) {
    this();
    first = head;
    stats.addWithKey(first, first.id);
    // post is always null!
  }

  public static @Nullable Statement isHead(Statement head) {
    if (head.getLastBasicType() == StatementType.GENERAL && !head.isMonitorEnter()) {
      // at most one outgoing edge
      StatEdge edge = null;
      List<StatEdge> successorEdges = head.getSuccessorEdges(EdgeType.DIRECT_ALL);
      if (!successorEdges.isEmpty()) {
        edge = successorEdges.get(0);
      }
      // regular loop
      if (edge != null && edge.getType() == EdgeType.REGULAR && edge.getDestination() == head) {
        return new DoStatement(head);
      }
      // continues
      if (head.type != StatementType.DO && (edge == null || edge.getType() != EdgeType.REGULAR) &&
          head.getContinueSet().contains(head.getBasichead())) {
        return new DoStatement(head);
      }
    }
    return null;
  }

  @Override
  public @NotNull TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();
    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));
    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(Integer.toString(id)).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }
    switch (loopType) {
      case DO:
        buf.appendIndent(indent).append("while(true) {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case DO_WHILE:
        buf.appendIndent(indent).append("do {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("} while(").append(
          Objects.requireNonNull(conditionExprent.get(0)).toJava(indent, tracer)).append(");").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case WHILE:
        buf.appendIndent(indent).append("while(").append(
          Objects.requireNonNull(conditionExprent.get(0)).toJava(indent, tracer)).append(") {").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        break;
      case FOR:
        buf.appendIndent(indent).append("for(");
        Exprent firstInitExprent = initExprent.get(0);
        if (firstInitExprent != null) {
          buf.append(firstInitExprent.toJava(indent, tracer));
        }
        Exprent firstIncExprent = Objects.requireNonNull(incExprent.get(0));
        buf.append("; ")
          .append(Objects.requireNonNull(conditionExprent.get(0)).toJava(indent, tracer)).append("; ").append(firstIncExprent.toJava(indent, tracer)).append(") {")
          .appendLineSeparator();
        tracer.incrementCurrentSourceLine();
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, false, tracer));
        buf.appendIndent(indent).append("}").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
    }
    return buf;
  }

  @Override
  public @NotNull List<Object> getSequentialObjects() {
    List<Object> lst = new ArrayList<>();
    switch (loopType) {
      case FOR:
        if (getInitExprent() != null) {
          lst.add(getInitExprent());
        }
      case WHILE:
        lst.add(getConditionExprent());
    }
    lst.add(first);
    switch (loopType) {
      case DO_WHILE:
        lst.add(getConditionExprent());
        break;
      case FOR:
        lst.add(getIncExprent());
    }
    return lst;
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (initExprent.get(0) == oldExpr) {
      initExprent.set(0, newExpr);
    }
    if (conditionExprent.get(0) == oldExpr) {
      conditionExprent.set(0, newExpr);
    }
    if (incExprent.get(0) == oldExpr) {
      incExprent.set(0, newExpr);
    }
  }

  @Override
  public @NotNull Statement getSimpleCopy() {
    return new DoStatement();
  }

  public @NotNull List<Exprent> getInitExprentList() {
    return initExprent;
  }

  public @NotNull List<Exprent> getConditionExprentList() {
    return conditionExprent;
  }

  public @NotNull List<Exprent> getIncExprentList() {
    return incExprent;
  }

  public @Nullable Exprent getConditionExprent() {
    return conditionExprent.get(0);
  }

  public void setConditionExprent(Exprent conditionExprent) {
    this.conditionExprent.set(0, conditionExprent);
  }

  public @Nullable Exprent getIncExprent() {
    return incExprent.get(0);
  }

  public void setIncExprent(Exprent incExprent) {
    this.incExprent.set(0, incExprent);
  }

  public @Nullable Exprent getInitExprent() {
    return initExprent.get(0);
  }

  public void setInitExprent(Exprent initExprent) {
    this.initExprent.set(0, initExprent);
  }

  public @NotNull LoopType getLoopType() {
    return loopType;
  }

  public void setLoopType(@NotNull LoopType loopType) {
    this.loopType = loopType;
  }

  public enum LoopType {
    DO,
    DO_WHILE,
    WHILE,
    FOR
  }
}
