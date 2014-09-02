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
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.ArrayList;
import java.util.List;


public class DoStatement extends Statement {

  public static final int LOOP_DO = 0;
  public static final int LOOP_DOWHILE = 1;
  public static final int LOOP_WHILE = 2;
  public static final int LOOP_FOR = 3;

  private int looptype;

  private List<Exprent> initExprent = new ArrayList<Exprent>();
  private List<Exprent> conditionExprent = new ArrayList<Exprent>();
  private List<Exprent> incExprent = new ArrayList<Exprent>();

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

  public String toJava(int indent) {
    String indstr = InterpreterUtil.getIndentString(indent);
    StringBuilder buf = new StringBuilder();

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent));

    if (isLabeled()) {
      buf.append(indstr).append("label").append(this.id).append(":").append(new_line_separator);
    }

    switch (looptype) {
      case LOOP_DO:
        buf.append(indstr).append("while(true) {").append(new_line_separator);
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true));
        buf.append(indstr).append("}").append(new_line_separator);
        break;
      case LOOP_DOWHILE:
        buf.append(indstr).append("do {").append(new_line_separator);
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true));
        buf.append(indstr).append("} while(").append(conditionExprent.get(0).toJava(indent)).append(");").append(new_line_separator);
        break;
      case LOOP_WHILE:
        buf.append(indstr).append("while(").append(conditionExprent.get(0).toJava(indent)).append(") {").append(new_line_separator);
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true));
        buf.append(indstr).append("}").append(new_line_separator);
        break;
      case LOOP_FOR:
        buf.append(indstr).append("for(").append(initExprent.get(0) == null ? "" : initExprent.get(0).toJava(indent)).append("; ")
          .append(conditionExprent.get(0).toJava(indent)).append("; ").append(incExprent.get(0).toJava(indent)).append(") {")
          .append(new_line_separator);
        buf.append(ExprProcessor.jmpWrapper(first, indent + 1, true));
        buf.append(indstr).append("}").append(new_line_separator);
    }

    return buf.toString();
  }

  public List<Object> getSequentialObjects() {

    List<Object> lst = new ArrayList<Object>();

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
