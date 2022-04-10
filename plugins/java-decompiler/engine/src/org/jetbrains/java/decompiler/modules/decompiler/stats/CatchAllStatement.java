// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class CatchAllStatement extends Statement {

  private Statement handler;

  private boolean isFinally;

  private VarExprent monitor;

  private final List<VarExprent> vars = new ArrayList<>();

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private CatchAllStatement() {
    super(StatementType.CATCH_ALL);
  }

  private CatchAllStatement(Statement head, Statement handler) {

    this();

    first = head;
    stats.addWithKey(head, head.id);

    this.handler = handler;
    stats.addWithKey(handler, handler.id);

    List<StatEdge> lstSuccs = head.getSuccessorEdges(EdgeType.DIRECT_ALL);
    if (!lstSuccs.isEmpty()) {
      StatEdge edge = lstSuccs.get(0);
      if (edge.getType() == EdgeType.REGULAR) {
        post = edge.getDestination();
      }
    }

    vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                            new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"),
                            DecompilerContext.getVarProcessor()));
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead(Statement head) {
    if (head.getLastBasicType() != StatementType.GENERAL) {
      return null;
    }

    Set<Statement> setHandlers = DecHelper.getUniquePredExceptions(head);
    if (setHandlers.size() != 1) {
      return null;
    }

    for (StatEdge edge : head.getSuccessorEdges(EdgeType.EXCEPTION)) {
      Statement exc = edge.getDestination();

      if (edge.getExceptions() == null && exc.getLastBasicType() == StatementType.GENERAL && setHandlers.contains(exc)) {
        List<StatEdge> lstSuccs = exc.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (lstSuccs.isEmpty() || lstSuccs.get(0).getType() != EdgeType.REGULAR) {

          if (head.isMonitorEnter() || exc.isMonitorEnter()) {
            return null;
          }

          if (DecHelper.checkStatementExceptions(Arrays.asList(head, exc))) {
            return new CatchAllStatement(head, exc);
          }
        }
      }
    }

    return null;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    String new_line_separator = DecompilerContext.getNewLineSeparator();

    TextBuffer buf = new TextBuffer();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));

    boolean labeled = isLabeled();
    if (labeled) {
      buf.appendIndent(indent).append("label").append(Integer.toString(id)).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    List<StatEdge> lstSuccs = first.getSuccessorEdges(EdgeType.DIRECT_ALL);
    if (first.type == StatementType.TRY_CATCH && first.varDefinitions.isEmpty() && isFinally &&
        !labeled && !first.isLabeled() && (lstSuccs.isEmpty() || !lstSuccs.get(0).explicit)) {
      TextBuffer content = ExprProcessor.jmpWrapper(first, indent, true, tracer);
      content.setLength(content.length() - new_line_separator.length());
      tracer.incrementCurrentSourceLine(-1);
      buf.append(content);
    }
    else {
      buf.appendIndent(indent).append("try {").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
      buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true, tracer));
      buf.appendIndent(indent).append("}");
    }

    buf.append(isFinally ? " finally" :
               " catch (" + vars.get(0).toJava(indent, tracer) + ")").append(" {").appendLineSeparator();
    tracer.incrementCurrentSourceLine();

    if (monitor != null) {
      buf.appendIndent(indent+1).append("if (").append(monitor.toJava(indent, tracer)).append(") {").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    buf.append(ExprProcessor.jmpWrapper(handler, indent + 1 + (monitor != null ? 1 : 0), true, tracer));

    if (monitor != null) {
      buf.appendIndent(indent + 1).append("}").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    buf.appendIndent(indent).append("}").appendLineSeparator();
    tracer.incrementCurrentSourceLine();

    return buf;
  }

  @Override
  public void replaceStatement(Statement oldstat, Statement newstat) {

    if (handler == oldstat) {
      handler = newstat;
    }

    super.replaceStatement(oldstat, newstat);
  }

  @Override
  public Statement getSimpleCopy() {

    CatchAllStatement cas = new CatchAllStatement();

    cas.isFinally = this.isFinally;

    if (this.monitor != null) {
      cas.monitor = new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                   VarType.VARTYPE_INT,
                                   DecompilerContext.getVarProcessor());
    }

    if (!this.vars.isEmpty()) {
      cas.vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                              new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"),
                              DecompilerContext.getVarProcessor()));
    }

    return cas;
  }

  @Override
  public void initSimpleCopy() {
    first = stats.get(0);
    handler = stats.get(1);
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public Statement getHandler() {
    return handler;
  }

  public boolean isFinally() {
    return isFinally;
  }

  public void setFinally(boolean isFinally) {
    this.isFinally = isFinally;
  }

  public VarExprent getMonitor() {
    return monitor;
  }

  public void setMonitor(VarExprent monitor) {
    this.monitor = monitor;
  }

  public List<VarExprent> getVars() {
    return vars;
  }
}