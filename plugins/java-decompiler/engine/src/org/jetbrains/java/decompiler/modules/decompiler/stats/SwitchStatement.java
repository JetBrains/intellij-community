// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.SwitchInstruction;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeDirection;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;

import static org.jetbrains.java.decompiler.ClassNameConstants.JAVA_LANG_CHARACTER;
import static org.jetbrains.java.decompiler.code.CodeConstants.TYPE_OBJECT;
import static org.jetbrains.java.decompiler.struct.gen.VarType.VARTYPE_CHAR;

public final class SwitchStatement extends Statement {
  private List<Statement> caseStatements = new ArrayList<>();
  private List<List<StatEdge>> caseEdges = new ArrayList<>();
  private List<List<@Nullable Exprent>> caseValues = new ArrayList<>();
  private final Map<Statement, Exprent> guards = new HashMap<>();
  private StatEdge defaultEdge;
  private Exprent headExprent;
  private boolean canBeRule = false;
  private boolean useCustomDefault = false;

  private SwitchStatement() {
    super(StatementType.SWITCH);
  }

  private SwitchStatement(@NotNull Statement head, @Nullable Statement postStatement) {
    this();
    first = head;
    stats.addWithKey(head, head.id);
    // find post node
    Set<Statement> regularSuccessors = new HashSet<>(head.getNeighbours(EdgeType.REGULAR, EdgeDirection.FORWARD));
    // cluster nodes
    if (postStatement != null) {
      post = postStatement;
      regularSuccessors.remove(post);
    }
    defaultEdge = head.getSuccessorEdges(EdgeType.DIRECT_ALL).get(0);
    for (Statement successor : regularSuccessors) {
      stats.addWithKey(successor, successor.id);
    }
  }

  public void setCanBeRule(boolean canBeRule) {
    this.canBeRule = canBeRule;
  }

  public void addGuard(@NotNull Statement statement, @NotNull Exprent guard) {
    guards.put(statement, guard);
  }


  /**
   * Removes the specified case statement from the switch statement.
   *
   * @param statement the statement to be removed
   */
  public void removeCaseStatement(@NotNull Statement statement) {
    stats.removeWithKey(statement.id);
    int caseIndex = caseStatements.indexOf(statement);
    if (caseIndex < 0) {
      return;
    }
    caseStatements.remove(caseIndex);
    caseEdges.remove(caseIndex);
    caseValues.remove(caseIndex);
    for (StatEdge edge : statement.getAllSuccessorEdges()) {
      edge.getDestination().removePredecessor(edge);
    }
    for (StatEdge edge : statement.getAllPredecessorEdges()) {
      edge.getSource().removeSuccessor(edge);
    }
  }


  /**
   * Duplicates a case statement within a switch statement.
   * Labels are not copied
   *
   * @param currentStatement The current case statement to duplicate.
   * @return The index of the duplicated case statement.
   *
   */
  public int duplicateCaseStatement(@NotNull Statement currentStatement) {
    Statement dummy = currentStatement.getSimpleCopy();
    int statIndex = stats.indexOf(currentStatement);
    if (statIndex < 0) {
      return statIndex;
    }
    stats.addWithKeyAndIndex(statIndex + 1, dummy, dummy.id);

    int caseIndex = caseStatements.indexOf(currentStatement);
    caseStatements.add(caseIndex + 1, dummy);
    List<@Nullable Exprent> toCopyValues = caseValues.get(caseIndex);
    caseValues.add(caseIndex + 1, toCopyValues);
    List<StatEdge> previousEdges = caseEdges.get(caseIndex);
    List<StatEdge> toCopyEdges = new ArrayList<>();
    for (StatEdge previousEdge : previousEdges) {
      StatEdge edge = previousEdge.copy();
      edge.setDestination(dummy);
      toCopyEdges.add(edge);
    }
    caseEdges.add(caseIndex + 1, toCopyEdges);

    for (StatEdge edge : currentStatement.getAllPredecessorEdges()) {
      StatEdge copy = edge.copy();
      copy.setDestination(dummy);
      copy.getSource().addSuccessor(copy);
    }

    for (StatEdge edge : currentStatement.getAllSuccessorEdges()) {
      StatEdge copy = edge.copy();
      copy.setSource(dummy);
      dummy.addSuccessor(copy);
    }

    dummy.setParent(this);
    return caseIndex + 1;
  }

  public void setUseCustomDefault() {
    useCustomDefault = true;
  }

  @Nullable
  public static Statement isHead(@NotNull Statement head) {
    if (head.type == StatementType.BASIC_BLOCK && head.getLastBasicType() == StatementType.SWITCH) {
      List<Statement> statements = new ArrayList<>();
      if (DecHelper.isChoiceStatement(head, statements)) {
        Statement post = statements.remove(0);
        for (Statement statement : statements) {
          if (statement.isMonitorEnter()) {
            return null;
          }
        }
        if (DecHelper.checkStatementExceptions(statements)) {
          return new SwitchStatement(head, post);
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public TextBuffer toJava(int indent, @NotNull BytecodeMappingTracer tracer) {
    SwitchHelper.simplifySwitchOnEnum(this);
    TextBuffer buf = new TextBuffer();
    buf.append(ExprProcessor.listToJava(varDefinitions, indent, tracer));
    buf.append(first.toJava(indent, tracer));
    if (isLabeled()) {
      buf.appendIndent(indent).append("label").append(Integer.toString(id)).append(":").appendLineSeparator();
      tracer.incrementCurrentSourceLine();
    }
    buf.appendIndent(indent).append(headExprent.toJava(indent, tracer)).append(" {").appendLineSeparator();
    tracer.incrementCurrentSourceLine();
    VarType switchType = headExprent.getExprType();
    for (int i = 0; i < caseStatements.size(); i++) {
      Statement stat = caseStatements.get(i);
      List<StatEdge> edges = caseEdges.get(i);
      List<Exprent> values = caseValues.get(i);
      for (int j = 0; j < edges.size(); j++) {
        if (edges.get(j) == defaultEdge && !useCustomDefault) {
          if (!canBeRule) {
            buf.appendIndent(indent + 1).append("default:").appendLineSeparator();
          }
          else {
            buf.appendIndent(indent + 1).append("default -> ");
          }
        }
        else {
          buf.appendIndent(indent + 1).append("case ");
          Exprent value = values.get(j);
          if (value instanceof ConstExprent constExprent && !constExprent.isNull()) {
            value = value.copy();
            if (switchType.getType() != TYPE_OBJECT) {
              ((ConstExprent)value).setConstType(switchType);
            }
            else if (((JAVA_LANG_CHARACTER).equals(switchType.getValue()))) {
              ((ConstExprent)value).setConstType(VARTYPE_CHAR);
            }
          }
          if (value instanceof FieldExprent && ((FieldExprent)value).isStatic()) { // enum values
            buf.append(((FieldExprent)value).getName());
          }
          else {
            buf.append(value.toJava(indent, tracer));
          }

          Exprent guard = guards.get(stat);
          if (guard != null) {
            buf.append(" when ").append(guard.toJava(0, tracer));
          }

          if (!canBeRule) {
            buf.append(":").appendLineSeparator();
          }
          else {
            buf.append(" -> ");
          }
        }
        if (!canBeRule) {
          tracer.incrementCurrentSourceLine();
        }
      }
      //example:
      //case 0: break
      if (canBeRule && stat instanceof BasicBlockStatement blockStatement && blockStatement.getBlock().getSeq().isEmpty() &&
          (stat.getExprents() == null || stat.getExprents().isEmpty())) {
        buf.append("{ }").appendLineSeparator();
        tracer.incrementCurrentSourceLine();
      }
      else {
        buf.append(ExprProcessor.jmpWrapper(stat, canBeRule ? 0 : indent + 2, false, tracer));
      }
    }
    buf.appendIndent(indent).append("}").appendLineSeparator();
    tracer.incrementCurrentSourceLine();
    return buf;
  }

  @Override
  public void initExprents() {
    SwitchExprent exprent = (SwitchExprent)first.getExprents().remove(first.getExprents().size() - 1);
    exprent.setCaseValues(caseValues);
    headExprent = exprent;
  }

  @Override
  @NotNull
  public List<Object> getSequentialObjects() {
    List<Object> result = new ArrayList<>(stats);
    result.add(1, headExprent);
    return result;
  }

  @Override
  public void replaceExprent(Exprent oldExprent, Exprent newExprent) {
    if (headExprent == oldExprent) {
      headExprent = newExprent;
    }
  }

  @Override
  public void replaceStatement(Statement oldStatement, Statement newStatement) {
    for (int i = 0; i < caseStatements.size(); i++) {
      if (caseStatements.get(i) == oldStatement) {
        caseStatements.set(i, newStatement);
      }
    }
    super.replaceStatement(oldStatement, newStatement);
  }

  @Override
  @NotNull
  public Statement getSimpleCopy() {
    return new SwitchStatement();
  }

  @Override
  public void initSimpleCopy() {
    first = stats.get(0);
    defaultEdge = first.getSuccessorEdges(EdgeType.DIRECT_ALL).get(0);
    sortEdgesAndNodes();
  }

  public void sortEdgesAndNodes() {
    Map<StatEdge, Integer> edgeIndicesMapping = new HashMap<>();
    List<StatEdge> firstSuccessors = first.getSuccessorEdges(EdgeType.DIRECT_ALL);
    for (int i = 0; i < firstSuccessors.size(); i++) {
      edgeIndicesMapping.put(firstSuccessors.get(i), i == 0 ? firstSuccessors.size() : i);
    }

    BasicBlockStatement firstBlock = (BasicBlockStatement)first;
    int[] values = ((SwitchInstruction)firstBlock.getBlock().getLastInstruction()).getValues();
    List<@Nullable Statement> caseStatements = new ArrayList<>(stats.size() - 1);
    List<List<Integer>> edgeIndices = new ArrayList<>(stats.size() - 1);
    collectRegularEdgesIndices(edgeIndicesMapping, caseStatements, edgeIndices);
    collectExitEdgesIndices(edgeIndicesMapping, caseStatements, edgeIndices);
    sortEdges(caseStatements, edgeIndices);

    List<List<StatEdge>> caseEdges = new ArrayList<>(edgeIndices.size());
    List<List<@Nullable Exprent>> caseValues = new ArrayList<>(edgeIndices.size());
    mapEdgeIndicesToEdges(values, edgeIndices, caseEdges, caseValues);

    replaceNullStatementsWithBasicBlocks(caseStatements, caseEdges);

    this.caseStatements = caseStatements;
    this.caseEdges = caseEdges;
    this.caseValues = caseValues;
  }

  private void mapEdgeIndicesToEdges(int[] values,
                                     @NotNull List<List<Integer>> edgeIndices,
                                     @NotNull List<List<StatEdge>> caseEdges,
                                     @NotNull List<List<@Nullable Exprent>> caseValues) {
    for (List<Integer> indices : edgeIndices) {
      List<StatEdge> edges = new ArrayList<>(indices.size());
      List<Exprent> valueExprents = new ArrayList<>(indices.size());
      List<StatEdge> firstSuccessors = first.getSuccessorEdges(EdgeType.DIRECT_ALL);
      for (Integer in : indices) {
        int index = in == firstSuccessors.size() ? 0 : in;
        edges.add(firstSuccessors.get(index));
        valueExprents.add(index == 0 ? null : new ConstExprent(values[index - 1], false, null));
      }
      caseEdges.add(edges);
      caseValues.add(valueExprents);
    }
  }

  private void collectRegularEdgesIndices(@NotNull Map<StatEdge, Integer> edgeIndicesMapping,
                                          @NotNull List<@Nullable Statement> nodes,
                                          @NotNull List<List<Integer>> edgeIndices) {
    for (int i = 1; i < stats.size(); i++) {
      Statement statement = stats.get(i);
      List<Integer> regularEdgeIndices = new ArrayList<>();
      for (StatEdge regularEdge : statement.getPredecessorEdges(EdgeType.REGULAR)) {
        if (regularEdge.getSource() == first) {
          regularEdgeIndices.add(edgeIndicesMapping.get(regularEdge));
        }
      }
      Collections.sort(regularEdgeIndices);
      nodes.add(statement);
      edgeIndices.add(regularEdgeIndices);
    }
  }

  private void collectExitEdgesIndices(@NotNull Map<StatEdge, Integer> edgeIndicesMapping,
                                       @NotNull List<@Nullable Statement> nodes,
                                       @NotNull List<List<Integer>> edgeIndices) {
    List<StatEdge> firstExitEdges = first.getSuccessorEdges(EdgeType.BREAK.unite(EdgeType.CONTINUE));
    while (!firstExitEdges.isEmpty()) {
      StatEdge exitEdge = firstExitEdges.get(0);
      List<Integer> exitEdgeIndices = new ArrayList<>();
      for (int i = firstExitEdges.size() - 1; i >= 0; i--) {
        StatEdge edgeTemp = firstExitEdges.get(i);
        if (edgeTemp.getDestination() == exitEdge.getDestination() && edgeTemp.getType() == exitEdge.getType()) {
          exitEdgeIndices.add(edgeIndicesMapping.get(edgeTemp));
          firstExitEdges.remove(i);
        }
      }
      Collections.sort(exitEdgeIndices);
      nodes.add(null);
      edgeIndices.add(exitEdgeIndices);
    }
  }

  private void sortEdges(List<@Nullable Statement> nodes, @NotNull List<List<Integer>> edgeIndices) {
    for (int i = 0; i < edgeIndices.size() - 1; i++) {
      for (int j = edgeIndices.size() - 1; j > i; j--) {
        if (edgeIndices.get(j - 1).get(0) > edgeIndices.get(j).get(0)) {
          edgeIndices.set(j, edgeIndices.set(j - 1, edgeIndices.get(j)));
          nodes.set(j, nodes.set(j - 1, nodes.get(j)));
        }
      }
    }
    for (int index = 0; index < nodes.size(); index++) {
      Statement node = nodes.get(index);
      if (node == null) continue;
      HashSet<Statement> nodePredecessors = new HashSet<>(node.getNeighbours(EdgeType.REGULAR, EdgeDirection.BACKWARD));
      nodePredecessors.remove(first);
      if (nodePredecessors.isEmpty()) continue;
      // assumption: at most one predecessor node besides the head. May not hold true for obfuscated code.
      Statement predecessor = nodePredecessors.iterator().next();
      for (int j = 0; j < nodes.size(); j++) {
        if (j != (index - 1) && nodes.get(j) == predecessor) {
          nodes.add(j + 1, node);
          edgeIndices.add(j + 1, edgeIndices.get(index));
          if (j > index) {
            nodes.remove(index);
            edgeIndices.remove(index);
            index--;
          }
          else {
            nodes.remove(index + 1);
            edgeIndices.remove(index + 1);
          }
          break;
        }
      }
    }
  }

  private void replaceNullStatementsWithBasicBlocks(List<@Nullable Statement> statements, @NotNull List<List<StatEdge>> edges) {
    for (int i = 0; i < statements.size(); i++) {
      if (statements.get(i) == null) {
        BasicBlockStatement basicBlock = new BasicBlockStatement(new BasicBlock(
          DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
        StatEdge sampleEdge = edges.get(i).get(0);
        basicBlock.addSuccessor(new StatEdge(sampleEdge.getType(), basicBlock, sampleEdge.getDestination(), sampleEdge.closure));
        for (StatEdge edge : edges.get(i)) {
          edge.getSource().changeEdgeType(EdgeDirection.FORWARD, edge, EdgeType.REGULAR);
          edge.closure.getLabelEdges().remove(edge);
          edge.getDestination().removePredecessor(edge);
          edge.getSource().changeEdgeNode(EdgeDirection.FORWARD, edge, basicBlock);
          basicBlock.addPredecessor(edge);
        }
        statements.set(i, basicBlock);
        stats.addWithKey(basicBlock, basicBlock.id);
        basicBlock.setParent(this);
      }
    }
  }

  @NotNull
  public List<Exprent> getHeadExprentList() {
    return Collections.singletonList(headExprent);
  }

  @Nullable
  public Exprent getHeadExprent() {
    return headExprent;
  }

  @NotNull
  public List<List<StatEdge>> getCaseEdges() {
    return caseEdges;
  }

  @NotNull
  public List<Statement> getCaseStatements() {
    return caseStatements;
  }

  @NotNull
  public StatEdge getDefaultEdge() {
    return defaultEdge;
  }

  @NotNull
  public List<List<@Nullable Exprent>> getCaseValues() {
    return caseValues;
  }
}
