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
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.Arrays;
import java.util.List;


public class SequenceStatement extends Statement {


  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private SequenceStatement() {
    type = Statement.TYPE_SEQUENCE;
  }

  public SequenceStatement(List<Statement> lst) {

    this();

    lastBasicType = lst.get(lst.size() - 1).getLastBasicType();

    for (Statement st : lst) {
      stats.addWithKey(st, st.id);
    }

    first = stats.get(0);
  }

  private SequenceStatement(Statement head, Statement tail) {

    this(Arrays.asList(head, tail));

    List<StatEdge> lstSuccs = tail.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    if (!lstSuccs.isEmpty()) {
      StatEdge edge = lstSuccs.get(0);

      if (edge.getType() == StatEdge.TYPE_REGULAR && edge.getDestination() != head) {
        post = edge.getDestination();
      }
    }
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead2Block(Statement head) {

    if (head.getLastBasicType() != Statement.LASTBASICTYPE_GENERAL) {
      return null;
    }

    // at most one outgoing edge
    StatEdge edge = null;
    List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    if (!lstSuccs.isEmpty()) {
      edge = lstSuccs.get(0);
    }

    if (edge != null && edge.getType() == StatEdge.TYPE_REGULAR) {
      Statement stat = edge.getDestination();

      if (stat != head && stat.getPredecessorEdges(StatEdge.TYPE_REGULAR).size() == 1
          && !stat.isMonitorEnter()) {

        if (stat.getLastBasicType() == Statement.LASTBASICTYPE_GENERAL) {
          if (DecHelper.checkStatementExceptions(Arrays.asList(head, stat))) {
            return new SequenceStatement(head, stat);
          }
        }
      }
    }

    return null;
  }

  public String toJava(int indent) {

    StringBuilder buf = new StringBuilder();

    String indstr = null;
    boolean islabeled = isLabeled();

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    buf.append(ExprProcessor.listToJava(varDefinitions, indent));

    if (islabeled) {
      indstr = InterpreterUtil.getIndentString(indent);
      indent++;
      buf.append(indstr).append("label").append(this.id).append(": {").append(new_line_separator);
    }

    boolean notempty = false;

    for (int i = 0; i < stats.size(); i++) {

      Statement st = stats.get(i);

      if (i > 0 && notempty) {
        buf.append(new_line_separator);
      }

      String str = ExprProcessor.jmpWrapper(st, indent, false);
      buf.append(str);

      notempty = (str.trim().length() > 0);
    }

    if (islabeled) {
      buf.append(indstr).append("}").append(new_line_separator);
    }

    return buf.toString();
  }

  public Statement getSimpleCopy() {
    return new SequenceStatement();
  }
}
