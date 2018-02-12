/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.SequenceHelper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;

import java.util.ArrayList;
import java.util.List;


public class SynchronizedStatement extends Statement {

  private Statement body;

  private final List<Exprent> headexprent = new ArrayList<>(1);

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public SynchronizedStatement() {
    type = TYPE_SYNCRONIZED;

    headexprent.add(null);
  }

  public SynchronizedStatement(Statement head, Statement body, Statement exc) {

    this();

    first = head;
    stats.addWithKey(head, head.id);

    this.body = body;
    stats.addWithKey(body, body.id);

    stats.addWithKey(exc, exc.id);

    List<StatEdge> lstSuccs = body.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    if (!lstSuccs.isEmpty()) {
      StatEdge edge = lstSuccs.get(0);
      if (edge.getType() == StatEdge.TYPE_REGULAR) {
        post = edge.getDestination();
      }
    }
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buf = new TextBuffer();
    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));
    buf.append(first.toJava(indent, tracer));

    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(this.id.toString()).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    buf.appendIndent(indent).append(headexprent.get(0).toJava(indent, tracer)).append(" {").appendLineSeparator();
    tracer.incrementCurrentSourceLine();

    buf.append(ExprProcessor.jmpWrapper(body, indent + 1, true, tracer));

    buf.appendIndent(indent).append("}").appendLineSeparator();
    mapMonitorExitInstr(tracer);
    tracer.incrementCurrentSourceLine();

    return buf;
  }

  private void mapMonitorExitInstr(BytecodeMappingTracer tracer) {
    BasicBlock block = body.getBasichead().getBlock();
    if (!block.getSeq().isEmpty() && block.getLastInstruction().opcode == CodeConstants.opc_monitorexit) {
      Integer offset = block.getOldOffset(block.size() - 1);
      if (offset > -1) tracer.addMapping(offset);
    }
  }

  public void initExprents() {
    headexprent.set(0, first.getExprents().remove(first.getExprents().size() - 1));
  }

  public List<Object> getSequentialObjects() {

    List<Object> lst = new ArrayList<>(stats);
    lst.add(1, headexprent.get(0));

    return lst;
  }

  public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
    if (headexprent.get(0) == oldexpr) {
      headexprent.set(0, newexpr);
    }
  }

  public void replaceStatement(Statement oldstat, Statement newstat) {

    if (body == oldstat) {
      body = newstat;
    }

    super.replaceStatement(oldstat, newstat);
  }

  public void removeExc() {
    Statement exc = stats.get(2);
    SequenceHelper.destroyStatementContent(exc, true);

    stats.removeWithKey(exc.id);
  }

  public Statement getSimpleCopy() {
    return new SynchronizedStatement();
  }

  public void initSimpleCopy() {
    first = stats.get(0);
    body = stats.get(1);
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public Statement getBody() {
    return body;
  }

  public List<Exprent> getHeadexprentList() {
    return headexprent;
  }
}