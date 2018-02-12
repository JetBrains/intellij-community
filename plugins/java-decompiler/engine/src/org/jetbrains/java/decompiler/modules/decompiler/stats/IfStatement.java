/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.IfExprent;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.ArrayList;
import java.util.List;


public class IfStatement extends Statement {

  public static final int IFTYPE_IF = 0;
  public static final int IFTYPE_IFELSE = 1;

  public int iftype;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private Statement ifstat;
  private Statement elsestat;

  private StatEdge ifedge;
  private StatEdge elseedge;

  private boolean negated = false;

  private final List<Exprent> headexprent = new ArrayList<>(1); // contains IfExprent

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  private IfStatement() {
    type = TYPE_IF;

    headexprent.add(null);
  }

  private IfStatement(Statement head, int regedges, Statement postst) {

    this();

    first = head;
    stats.addWithKey(head, head.id);

    List<StatEdge> lstHeadSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);

    switch (regedges) {
      case 0:
        ifstat = null;
        elsestat = null;

        break;
      case 1:
        ifstat = null;
        elsestat = null;

        StatEdge edgeif = lstHeadSuccs.get(1);
        if (edgeif.getType() != StatEdge.TYPE_REGULAR) {
          post = lstHeadSuccs.get(0).getDestination();
        }
        else {
          post = edgeif.getDestination();
          negated = true;
        }
        break;
      case 2:
        elsestat = lstHeadSuccs.get(0).getDestination();
        ifstat = lstHeadSuccs.get(1).getDestination();

        List<StatEdge> lstSucc = ifstat.getSuccessorEdges(StatEdge.TYPE_REGULAR);
        List<StatEdge> lstSucc1 = elsestat.getSuccessorEdges(StatEdge.TYPE_REGULAR);

        if (ifstat.getPredecessorEdges(StatEdge.TYPE_REGULAR).size() > 1 || lstSucc.size() > 1) {
          post = ifstat;
        }
        else if (elsestat.getPredecessorEdges(StatEdge.TYPE_REGULAR).size() > 1 || lstSucc1.size() > 1) {
          post = elsestat;
        }
        else {
          if (lstSucc.size() == 0) {
            post = elsestat;
          }
          else if (lstSucc1.size() == 0) {
            post = ifstat;
          }
        }

        if (ifstat == post) {
          if (elsestat != post) {
            ifstat = elsestat;
            negated = true;
          }
          else {
            ifstat = null;
          }
          elsestat = null;
        }
        else if (elsestat == post) {
          elsestat = null;
        }
        else {
          post = postst;
        }

        if (elsestat == null) {
          regedges = 1;  // if without else
        }
    }

    ifedge = lstHeadSuccs.get(negated ? 0 : 1);
    elseedge = (regedges == 2) ? lstHeadSuccs.get(negated ? 1 : 0) : null;

    iftype = (regedges == 2) ? IFTYPE_IFELSE : IFTYPE_IF;

    if (iftype == IFTYPE_IF) {
      if (regedges == 0) {
        StatEdge edge = lstHeadSuccs.get(0);
        head.removeSuccessor(edge);
        edge.setSource(this);
        this.addSuccessor(edge);
      }
      else if (regedges == 1) {
        StatEdge edge = lstHeadSuccs.get(negated ? 1 : 0);
        head.removeSuccessor(edge);
      }
    }

    if (ifstat != null) {
      stats.addWithKey(ifstat, ifstat.id);
    }

    if (elsestat != null) {
      stats.addWithKey(elsestat, elsestat.id);
    }

    if (post == head) {
      post = this;
    }
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public static Statement isHead(Statement head) {

    if (head.type == TYPE_BASICBLOCK && head.getLastBasicType() == LASTBASICTYPE_IF) {
      int regsize = head.getSuccessorEdges(StatEdge.TYPE_REGULAR).size();

      Statement p = null;

      boolean ok = (regsize < 2);
      if (!ok) {
        List<Statement> lst = new ArrayList<>();
        if (DecHelper.isChoiceStatement(head, lst)) {
          p = lst.remove(0);

          for (Statement st : lst) {
            if (st.isMonitorEnter()) {
              return null;
            }
          }

          ok = DecHelper.checkStatementExceptions(lst);
        }
      }

      if (ok) {
        return new IfStatement(head, regsize, p);
      }
    }

    return null;
  }

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

    if (ifstat == null) {
      buf.appendIndent(indent + 1);

      if (ifedge.explicit) {
        if (ifedge.getType() == StatEdge.TYPE_BREAK) {
          // break
          buf.append("break");
        }
        else {
          // continue
          buf.append("continue");
        }

        if (ifedge.labeled) {
          buf.append(" label").append(ifedge.closure.id.toString());
        }
      }
      buf.append(";").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }
    else {
      buf.append(ExprProcessor.jmpWrapper(ifstat, indent + 1, true, tracer));
    }

    boolean elseif = false;

    if (elsestat != null) {
      if (elsestat.type == Statement.TYPE_IF
          && elsestat.varDefinitions.isEmpty() && elsestat.getFirst().getExprents().isEmpty() &&
          !elsestat.isLabeled() &&
          (elsestat.getSuccessorEdges(STATEDGE_DIRECT_ALL).isEmpty()
           || !elsestat.getSuccessorEdges(STATEDGE_DIRECT_ALL).get(0).explicit)) { // else if
        buf.appendIndent(indent).append("} else ");

        TextBuffer content = ExprProcessor.jmpWrapper(elsestat, indent, false, tracer);
        content.setStart(TextUtil.getIndentString(indent).length());
        buf.append(content);

        elseif = true;
      }
      else {
        BytecodeMappingTracer else_tracer = new BytecodeMappingTracer(tracer.getCurrentSourceLine() + 1);
        TextBuffer content = ExprProcessor.jmpWrapper(elsestat, indent + 1, false, else_tracer);

        if (content.length() > 0) {
          buf.appendIndent(indent).append("} else {").appendLineSeparator();

          tracer.setCurrentSourceLine(else_tracer.getCurrentSourceLine());
          tracer.addTracer(else_tracer);

          buf.append(content);
        }
      }
    }

    if (!elseif) {
      buf.appendIndent(indent).append("}").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }

    return buf;
  }

  public void initExprents() {

    IfExprent ifexpr = (IfExprent)first.getExprents().remove(first.getExprents().size() - 1);

    if (negated) {
      ifexpr = (IfExprent)ifexpr.copy();
      ifexpr.negateIf();
    }

    headexprent.set(0, ifexpr);
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

    super.replaceStatement(oldstat, newstat);

    if (ifstat == oldstat) {
      ifstat = newstat;
    }

    if (elsestat == oldstat) {
      elsestat = newstat;
    }

    List<StatEdge> lstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL);

    if (iftype == IFTYPE_IF) {
      ifedge = lstSuccs.get(0);
      elseedge = null;
    }
    else {
      StatEdge edge0 = lstSuccs.get(0);
      StatEdge edge1 = lstSuccs.get(1);
      if (edge0.getDestination() == ifstat) {
        ifedge = edge0;
        elseedge = edge1;
      }
      else {
        ifedge = edge1;
        elseedge = edge0;
      }
    }
  }

  public Statement getSimpleCopy() {

    IfStatement is = new IfStatement();
    is.iftype = this.iftype;
    is.negated = this.negated;

    return is;
  }

  public void initSimpleCopy() {

    first = stats.get(0);

    List<StatEdge> lstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL);
    ifedge = lstSuccs.get((iftype == IFTYPE_IF || negated) ? 0 : 1);
    if (stats.size() > 1) {
      ifstat = stats.get(1);
    }

    if (iftype == IFTYPE_IFELSE) {
      elseedge = lstSuccs.get(negated ? 1 : 0);
      elsestat = stats.get(2);
    }
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public Statement getElsestat() {
    return elsestat;
  }

  public void setElsestat(Statement elsestat) {
    this.elsestat = elsestat;
  }

  public Statement getIfstat() {
    return ifstat;
  }

  public void setIfstat(Statement ifstat) {
    this.ifstat = ifstat;
  }

  public boolean isNegated() {
    return negated;
  }

  public void setNegated(boolean negated) {
    this.negated = negated;
  }

  public List<Exprent> getHeadexprentList() {
    return headexprent;
  }

  public IfExprent getHeadexprent() {
    return (IfExprent)headexprent.get(0);
  }

  public void setElseEdge(StatEdge elseedge) {
    this.elseedge = elseedge;
  }

  public void setIfEdge(StatEdge ifedge) {
    this.ifedge = ifedge;
  }

  public StatEdge getIfEdge() {
    return ifedge;
  }

  public StatEdge getElseEdge() {
    return elseedge;
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public IMatchable findObject(MatchNode matchNode, int index) {
    IMatchable object = super.findObject(matchNode, index);
    if (object != null) {
      return object;
    }

    if (matchNode.getType() == MatchNode.MATCHNODE_EXPRENT) {
      String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
      if ("head".equals(position)) {
        return getHeadexprent();
      }
    }

    return null;
  }

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (!super.match(matchNode, engine)) {
      return false;
    }

    Integer type = (Integer)matchNode.getRuleValue(MatchProperties.STATEMENT_IFTYPE);
    return type == null || this.iftype == type;
  }
}