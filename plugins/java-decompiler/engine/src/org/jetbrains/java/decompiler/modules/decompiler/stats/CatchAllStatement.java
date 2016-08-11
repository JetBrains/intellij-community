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
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class CatchAllStatement extends Statement {

  private Statement handler;

  private boolean isFinally;

  private VarExprent monitor;

  private final List<VarExprent> vars = new ArrayList<>();

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private CatchAllStatement() {
    type = Statement.TYPE_CATCHALL;
  }

  private CatchAllStatement(Statement head, Statement handler) {

    this();

    first = head;
    stats.addWithKey(head, head.id);

    this.handler = handler;
    stats.addWithKey(handler, handler.id);

    List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    if (!lstSuccs.isEmpty()) {
      StatEdge edge = lstSuccs.get(0);
      if (edge.getType() == StatEdge.TYPE_REGULAR) {
        post = edge.getDestination();
      }
    }

    vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                            new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"),
                            (VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead(Statement head) {

    if (head.getLastBasicType() != Statement.LASTBASICTYPE_GENERAL) {
      return null;
    }

    HashSet<Statement> setHandlers = DecHelper.getUniquePredExceptions(head);

    if (setHandlers.size() != 1) {
      return null;
    }

    for (StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
      Statement exc = edge.getDestination();

      if (edge.getExceptions() == null && setHandlers.contains(exc) && exc.getLastBasicType() == LASTBASICTYPE_GENERAL) {
        List<StatEdge> lstSuccs = exc.getSuccessorEdges(STATEDGE_DIRECT_ALL);
        if (lstSuccs.isEmpty() || lstSuccs.get(0).getType() != StatEdge.TYPE_REGULAR) {

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

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    String new_line_separator = DecompilerContext.getNewLineSeparator();

    TextBuffer buf = new TextBuffer();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));

    boolean labeled = isLabeled();
    if (labeled) {
      buf.appendIndent(indent).append("label").append(this.id.toString()).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    List<StatEdge> lstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    if (first.type == TYPE_TRYCATCH && first.varDefinitions.isEmpty() && isFinally &&
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
      buf.appendIndent(indent+1).append("if(").append(monitor.toJava(indent, tracer)).append(") {").appendLineSeparator();
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

  public void replaceStatement(Statement oldstat, Statement newstat) {

    if (handler == oldstat) {
      handler = newstat;
    }

    super.replaceStatement(oldstat, newstat);
  }

  public Statement getSimpleCopy() {

    CatchAllStatement cas = new CatchAllStatement();

    cas.isFinally = this.isFinally;

    if (this.monitor != null) {
      cas.monitor = new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                   VarType.VARTYPE_INT,
                                   (VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR));
    }

    if (!this.vars.isEmpty()) {
      // FIXME: WTF??? vars?!
      vars.add(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                              new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"),
                              (VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
    }

    return cas;
  }

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


  public void setHandler(Statement handler) {
    this.handler = handler;
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
