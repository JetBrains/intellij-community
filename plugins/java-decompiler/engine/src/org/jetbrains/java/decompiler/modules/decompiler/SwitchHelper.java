// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.ClassNameConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jetbrains.java.decompiler.ClassNameConstants.JAVA_LANG_OBJECT;
import static org.jetbrains.java.decompiler.code.CodeConstants.*;
import static org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import static org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.EXIT_THROW;
import static org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent.FUNCTION_CAST;
import static org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.IFTYPE_IF;
import static org.jetbrains.java.decompiler.struct.gen.VarType.*;

public final class SwitchHelper {

  /**
   * Method simplifies <code>switchStatement</code> if it represents switch-on-enum pattern.
   *
   * @param switchStatement statement to transform
   */
  public static void simplifySwitchOnEnum(@NotNull SwitchStatement switchStatement) {
    SwitchExprent switchExprent = (SwitchExprent)switchStatement.getHeadExprent();
    Exprent value = Objects.requireNonNull(switchExprent).getValue();
    if (!isEnumArray(value)) return;
    List<List<@Nullable Exprent>> caseValues = switchStatement.getCaseValues();
    ArrayExprent array = (ArrayExprent)value;
    Map<Exprent, Exprent> mapping = evaluateCaseLabelsToFieldsMapping(caseValues, array);
    List<List<@Nullable Exprent>> realCaseValues = findRealCaseValues(caseValues, mapping);
    if (realCaseValues == null) return;
    caseValues.clear();
    caseValues.addAll(realCaseValues);
    switchExprent.replaceExprent(value, ((InvocationExprent)array.getIndex()).getInstance().copy());
  }

  /**
   * Method searches and simplifies "switch-on-references" patterns in the statement graph.
   *
   * @param root statement to start traversal
   */
  public static void simplifySwitchesOnReferences(@NotNull RootStatement root) {
    List<SwitchOnCandidate> candidates = new ArrayList<>();
    collectSwitchesOn(root, new ArrayList<>(
      Arrays.asList(new StringSwitchRecognizer.JavacStringRecognizer(), new StringSwitchRecognizer.EcjStringRecognizer(),
                    new JavacReferenceRecognizer()
      )), candidates);
    if (candidates.isEmpty()) return;
    List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();
    candidates.forEach(candidate -> candidate.simplify(tempVarAssignments));
    removeTempVariableDeclarations(tempVarAssignments);
  }

  private static void collectSwitchesOn(@NotNull Statement statement,
                                        @NotNull List<SwitchRecognizer> recognizers,
                                        @NotNull List<SwitchOnCandidate> candidates) {
    if (statement instanceof SwitchStatement switchStatement) {
      SwitchExprent switchExprent = (SwitchExprent)switchStatement.getHeadExprent();
      Exprent switchSelector = Objects.requireNonNull(switchExprent).getValue();
      if (switchSelector instanceof InvocationExprent) {
        SwitchRecognizer usedRecognizer = null;
        for (SwitchRecognizer recognizer : recognizers) {
          SwitchOnCandidate switchCandidate = recognizer.recognize(switchStatement, (InvocationExprent)switchSelector);
          if (switchCandidate == null) continue;
          candidates.add(switchCandidate);
          usedRecognizer = recognizer;
          break;
        }
        if (usedRecognizer != null) {
          // most probably we need to leave only one recognizer
          recognizers.retainAll(Collections.singletonList(usedRecognizer));
        }
      }
    }
    for (Statement child : statement.getStats()) {
      collectSwitchesOn(child, recognizers, candidates);
    }
  }

  @NotNull
  private static Map<Exprent, Exprent> evaluateCaseLabelsToFieldsMapping(@NotNull List<List<Exprent>> caseValues,
                                                                         @NotNull ArrayExprent array) {
    Map<Exprent, Exprent> mapping = new HashMap<>(caseValues.size());
    if (array.getArray().type == Exprent.EXPRENT_FIELD) { // Javac compiler
      FieldExprent arrayField = (FieldExprent)array.getArray();
      ClassesProcessor.ClassNode classNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(arrayField.getClassname());
      if (classNode == null) return mapping;
      MethodWrapper wrapper = classNode.getWrapper().getMethodWrapper(CLINIT_NAME, "()V");
      if (wrapper != null && wrapper.root != null) {
        wrapper.getOrBuildGraph().iterateExprents(exprent -> {
          if (exprent instanceof AssignmentExprent assignment) {
            Exprent left = assignment.getLeft();
            if (left.type == Exprent.EXPRENT_ARRAY && ((ArrayExprent)left).getArray().equals(arrayField)) {
              mapping.put(assignment.getRight(), ((InvocationExprent)((ArrayExprent)left).getIndex()).getInstance());
            }
          }
          return 0;
        });
      }
    }
    else if (array.getArray().type == Exprent.EXPRENT_INVOCATION) { // Eclipse compiler
      InvocationExprent invocationExprent = (InvocationExprent)array.getArray();
      ClassesProcessor.ClassNode classNode =
        DecompilerContext.getClassProcessor().getMapRootClasses().get(invocationExprent.getClassName());
      if (classNode == null) return mapping;
      MethodWrapper wrapper = classNode.getWrapper().getMethodWrapper(invocationExprent.getName(), "()[I");
      if (wrapper != null && wrapper.root != null) {
        wrapper.getOrBuildGraph().iterateExprents(exprent -> {
          if (exprent instanceof AssignmentExprent assignment) {
            Exprent left = assignment.getLeft();
            if (left.type == Exprent.EXPRENT_ARRAY) {
              Exprent indexExprent = ((ArrayExprent)left).getIndex();
              if (indexExprent.type == Exprent.EXPRENT_INVOCATION && ((InvocationExprent)indexExprent).getName().equals("ordinal")) {
                mapping.put(assignment.getRight(), ((InvocationExprent)((ArrayExprent)left).getIndex()).getInstance());
              }
            }
          }
          return 0;
        });
      }
    }
    return mapping;
  }

  @Nullable
  private static List<List<@Nullable Exprent>> findRealCaseValues(@NotNull List<List<Exprent>> caseValues,
                                                                  @NotNull Map<Exprent, Exprent> mapping) {
    List<List<@Nullable Exprent>> result = new ArrayList<>(caseValues.size());
    for (List<Exprent> caseValue : caseValues) {
      List<@Nullable Exprent> values = new ArrayList<>(caseValue.size());
      result.add(values);
      for (Exprent exprent : caseValue) {
        if (exprent == null) {
          values.add(null);
        }
        else {
          Exprent realConst = mapping.get(exprent);
          if (realConst == null) {
            DecompilerContext.getLogger()
              .writeMessage("Unable to simplify switch on enum: " + exprent + " not found, available: " + mapping, Severity.ERROR);
            return null;
          }
          values.add(realConst.copy());
        }
      }
    }
    return result;
  }

  private static boolean isEnumArray(Exprent exprent) {
    if (!(exprent instanceof ArrayExprent)) return false;
    Exprent field = ((ArrayExprent)exprent).getArray();
    Exprent index = ((ArrayExprent)exprent).getIndex();
    boolean isJavacEnumArray = field instanceof FieldExprent &&
                               (((FieldExprent)field).getName().startsWith("$SwitchMap") ||
                                (index instanceof InvocationExprent && ((InvocationExprent)index).getName().equals("ordinal")));
    boolean isEclipseEnumArray = field instanceof InvocationExprent &&
                                 ((InvocationExprent)field).getName().startsWith("$SWITCH_TABLE");
    return isJavacEnumArray || isEclipseEnumArray;
  }

  record TempVarAssignmentItem(@NotNull VarExprent varExprent, @NotNull Statement statement) {

  }

  @SuppressWarnings("SSBasedInspection")
  static void removeTempVariableDeclarations(@NotNull List<TempVarAssignmentItem> tempVarAssignments) {
    if (tempVarAssignments.isEmpty()) return;
    Set<Statement> visited = new HashSet<>();
    Set<Statement> statements = tempVarAssignments.stream().map(a -> a.statement()).collect(Collectors.toSet());
    Set<VarExprent> vars = tempVarAssignments.stream().map(a -> a.varExprent()).collect(Collectors.toSet());
    for (Statement statement : statements) {
      Statement parent = statement;
      while (parent != null) {
        if (visited.contains(parent)) {
          break;
        }
        visited.add(parent);
        List<Exprent> candidates;
        if (parent.getFirst() != null && parent.getFirst().type == StatementType.BASIC_BLOCK) {
          candidates = parent.getFirst().getExprents();
        }
        else if (parent.type == StatementType.TRY_CATCH || parent.type == StatementType.SEQUENCE) {
          candidates = parent.getVarDefinitions();
        }
        else {
          candidates = Collections.emptyList();
        }
        if (candidates == null) {
          candidates = new ArrayList<>();
        }
        List<List<Exprent>> listVarExprents = new ArrayList<>();
        listVarExprents.add(candidates);
        if (parent.getExprents() != null) {
          listVarExprents.add(parent.getExprents());
        }
        List<Exprent> toDelete = new ArrayList<>();
        for (List<Exprent> varExprents : listVarExprents) {
          for (int i = 0; i < varExprents.size(); i++) {
            Exprent exprent = varExprents.get(i);
            Exprent assignmentExprent = null;
            if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              assignmentExprent = exprent;
              exprent = ((AssignmentExprent)exprent).getLeft();
            }
            if (exprent.type != Exprent.EXPRENT_VAR) continue;
            VarExprent varExprent = (VarExprent)exprent;
            if (varExprent.isDefinition() && vars.stream()
              .anyMatch(expr -> expr.getIndex() == varExprent.getIndex() && expr.getVersion() == varExprent.getVersion())) {
                toDelete.add(assignmentExprent == null ? varExprent : assignmentExprent);
            }
          }
          varExprents.removeAll(toDelete);
        }
        parent = parent.getParent();
      }
    }
  }

  public static void prepareForRules(@NotNull Statement statement, @NotNull StructClass cl) {
    if (!cl.hasEnhancedSwitchSupport()) {
      return;
    }
    if (statement instanceof SwitchStatement switchStatement) {
      if (canBeRules(switchStatement)) {
        switchStatement.setCanBeRule(true);
        prepareForRules(switchStatement);
      }
    }
    for (Statement child : statement.getStats()) {
      prepareForRules(child, cl);
    }
  }

  private static boolean canBeRules(@NotNull SwitchStatement statement) {
    if (statement.isLabeled()) {
      return false;
    }
    //only for simplification
    for (List<Exprent> value : statement.getCaseValues()) {
      if (value.size() != 1) {
        return false;
      }
    }

    for (Statement caseStatement : statement.getCaseStatements()) {
      //only for simplification and not to create long rules
      if (caseStatement instanceof SequenceStatement || !caseStatement.getStats().isEmpty() ||
          caseStatement.getExprents() != null &&
          caseStatement.getExprents().size() > 1) {
        return false;
      }
      List<StatEdge> successorEdges = caseStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
      if (successorEdges.size() != 1) {
        return false;
      }
      StatEdge edge = successorEdges.get(0);
      if (edge.getType() != EdgeType.BREAK) {
        return false;
      }
    }
    return true;
  }

  private static void prepareForRules(@NotNull SwitchStatement statement) {
    for (Statement caseStatement : statement.getCaseStatements()) {
      List<StatEdge> successorEdges = caseStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
      if (successorEdges.size() != 1) {
        continue;
      }
      StatEdge edge = successorEdges.get(0);
      if (edge.getType() == EdgeType.BREAK && edge.explicit && !edge.labeled) {
        edge.explicit = false;
      }
    }
  }

  private interface SwitchRecognizer {
    @Nullable
    SwitchOnCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector);
  }


  private static boolean isBootstrapSwitch(@Nullable Exprent headExprent) {
    if (!(headExprent instanceof SwitchExprent switchExprent)) {
      return false;
    }
    Exprent switchSelector = switchExprent.getValue();
    if (switchSelector instanceof InvocationExprent invocationExprent) {
      if (invocationExprent.isDynamicCall("typeSwitch", 2)) return true;
      if (invocationExprent.isDynamicCall("enumSwitch", 2)) return true;
    }
    return false;
  }

  private static class JavacReferenceRecognizer implements SwitchRecognizer {


    record FullCase(@NotNull Statement statement, @NotNull List<Exprent> exprents, @NotNull List<StatEdge> edges) {
    }

    record CaseValueWithEdge(@Nullable Exprent exprent, @Nullable StatEdge edge) {
    }

    @SuppressWarnings({"SSBasedInspection", "SimplifyStreamApiCallChains"})
    @Nullable
    private static List<FullCase> resortForSwitchBootstrap(@NotNull SwitchStatement statement,
                                                           boolean mustCleanNonNull) {
      for (Statement caseStatement : statement.getCaseStatements()) {
        if (caseStatement == null) {
          return null;
        }
      }
      @NotNull List<List<@Nullable Exprent>> values = statement.getCaseValues();
      List<List<Exprent>> sortedCaseValue = new ArrayList<>();
      List<List<StatEdge>> sortedEdges = new ArrayList<>();
      for (int i = 0; i < values.size(); i++) {
        List<Exprent> caseValue = statement.getCaseValues().get(i);
        for (Exprent exprent : caseValue) {
          if (exprent == null) {
            continue;
          }
          if (!(exprent instanceof ConstExprent constCaseValue)) {
            return null;
          }
          if (!(constCaseValue.getConstType() == VARTYPE_INT ||
                constCaseValue.getConstType() == VARTYPE_BYTECHAR ||
                constCaseValue.getConstType() == VARTYPE_CHAR ||
                constCaseValue.getConstType() == VARTYPE_BYTE)) {
            return null;
          }
        }
        List<StatEdge> edges = statement.getCaseEdges().get(i);
        if (edges.size() != caseValue.size()) {
          return null;
        }
        List<CaseValueWithEdge> sorted =
          IntStream.range(0, edges.size()).mapToObj(ind -> new CaseValueWithEdge(caseValue.get(ind), edges.get(ind)))
            .sorted(
              Comparator.<CaseValueWithEdge, Boolean>comparing(o -> o.edge == statement.getDefaultEdge())
                .thenComparingLong(o -> o.exprent instanceof ConstExprent c ? c.getIntValue() : Long.MIN_VALUE))
            .collect(Collectors.toList());
        sortedEdges.add(sorted.stream().map(t -> t.edge).collect(Collectors.toList()));
        sortedCaseValue.add(sorted.stream().map(t -> t.exprent).collect(Collectors.toList()));
      }

      List<FullCase> sortedAll = IntStream.range(0, statement.getCaseValues().size())
        .mapToObj(ind -> new FullCase(statement.getCaseStatements().get(ind),
                                      sortedCaseValue.get(ind),
                                      sortedEdges.get(ind)))
        .sorted(Comparator.<FullCase, Boolean>comparing(
            fullCase -> !fullCase.edges().isEmpty() && fullCase.edges().get(fullCase.exprents.size() - 1) == statement.getDefaultEdge())
                  .thenComparingLong(o -> !o.exprents.isEmpty() && o.exprents.get(o.exprents.size() - 1) instanceof ConstExprent c
                                          ? c.getIntValue()
                                          : Long.MIN_VALUE))
        .collect(Collectors.toList());

      for (int i = 0; i < sortedAll.size(); i++) {
        if (i == sortedAll.size() - 1) {
          break;
        }
        FullCase current = sortedAll.get(i);
        FullCase next = sortedAll.get(i + 1);
        if (current.exprents.isEmpty() || next.exprents.isEmpty()) {
          return null;
        }

        Exprent currentFirstExpr = current.exprents.get(0);
        Exprent nextFirstExpr = next.exprents.get(0);
        if (currentFirstExpr instanceof ConstExprent constExprent1 && nextFirstExpr instanceof ConstExprent constExprent2) {
          if (constExprent1.getIntValue() > constExprent2.getIntValue()) {
            if (statement.getDefaultEdge() != next.edges.get(next.edges.size() - 1)) {
              return null;
            }
          }
        }
        List<StatEdge> edges = current.statement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.size() == 1) {
          StatEdge currentEdge = edges.get(0);
          if (currentEdge.getType() == EdgeType.REGULAR) {
            Statement destination = currentEdge.getDestination();
            if (destination != next.statement()) {
              return null;
            }
          }
        }
      }

      for (int i = 0; i < sortedAll.size(); i++) {
        Statement currentStatement = sortedAll.get(i).statement;
        List<StatEdge> edges = currentStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.size() == 1) {
          StatEdge onlyEdge = edges.get(0);
          if (onlyEdge.getType() != EdgeType.REGULAR && !onlyEdge.explicit) {
            onlyEdge.explicit = true;
            onlyEdge.labeled = false;
          }
          if (i == statement.getCaseStatements().size() - 1 &&
              onlyEdge.getType() == EdgeType.BREAK &&
              onlyEdge.explicit &&
              !onlyEdge.labeled) {
            onlyEdge.explicit = false;
          }
        }
      }

      if (mustCleanNonNull) {
        FullCase toDelete = null;
        for (FullCase fullCase : sortedAll) {
          int nullIndex = -1;
          List<Exprent> exprents = fullCase.exprents;
          for (int i = 0; i < exprents.size(); i++) {
            Exprent t = exprents.get(i);
            if (t instanceof ConstExprent constExprent && constExprent.getIntValue() == -1) {
              nullIndex = i;
              break;
            }
          }
          if (nullIndex == -1) {
            continue;
          }
          if (fullCase.exprents().size() != 1) {
            fullCase.exprents.remove(nullIndex);
            fullCase.edges.remove(nullIndex);
          }
          else {
            toDelete = fullCase;
          }
          break;
        }
        sortedAll.remove(toDelete);
      }
      return sortedAll;
    }

    @Override
    public @Nullable SwitchOnCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector) {
      //now only with null, it is first step before patterns
      Exprent instance = switchSelector.getInstance();
      if (instance == null) {
        return null;
      }
      if (statement.getHeadExprent() == null) {
        return null;
      }
      if (instance.type != Exprent.EXPRENT_VAR) return null;
      //todo records
      if (!isBootstrapSwitch(statement.getHeadExprent())) return null;

      List<PooledConstant> bootstrapArguments = switchSelector.getBootstrapArguments();
      if (bootstrapArguments == null) {
        return null;
      }
      for (PooledConstant bootstrapArgument : bootstrapArguments) {
        if (!(bootstrapArgument instanceof PrimitiveConstant primitiveConstant)) {
          return null;
        }
        int type = primitiveConstant.type;
        if (!(type == CONSTANT_Integer || type == CONSTANT_String || type == CONSTANT_Class)) {
          return null;
        }
      }
      List<Exprent> parameters = switchSelector.getParameters();
      if (!instance.equals(parameters.get(0))) {
        return null;
      }
      Exprent typeVar = parameters.get(1);
      Statement first = statement.getFirst();
      if (first == null) {
        return null;
      }
      List<Exprent> firstExprents = first.getExprents();

      Statement doParentStatement = null;

      //possible structure:
      //SequenceStatement
      //  ....
      //  BasicBlockStatement
      //    exprents with init values
      //  DoStatement
      //    SwitchStatement
      //  ....
      if (firstExprents == null || firstExprents.isEmpty()) {
        Statement parent = statement.getParent();
        DoStatement doStatement = null;
        if (parent instanceof DoStatement) {
          doStatement = (DoStatement)parent;
        }
        if (doStatement == null && statement.getParent().getParent() instanceof DoStatement nextStatement) {
          doStatement = nextStatement;
        }
        if (doStatement!=null &&
            doStatement.getLoopType() == DoStatement.LoopType.DO &&
            doStatement.getStats().size() == 1 &&
            doStatement.getParent() instanceof SequenceStatement upperStatement &&
            upperStatement.getExprents() == null) {
          VBStyleCollection<Statement, Integer> upperStatementStats = upperStatement.getStats();
          int indexOfDo = upperStatementStats.indexOf(doStatement);
          if (indexOfDo == -1 || indexOfDo == 0) {
            return null;
          }
          if (upperStatementStats.get(indexOfDo) == doStatement &&
              upperStatementStats.get(indexOfDo - 1) instanceof BasicBlockStatement basicBlockStatement &&
              basicBlockStatement.getStats().isEmpty() &&
              basicBlockStatement.getExprents() != null &&
              !basicBlockStatement.getExprents().isEmpty()) {
            firstExprents = basicBlockStatement.getExprents();
            doParentStatement = doStatement;
            first = basicBlockStatement;
          } else if(upperStatementStats.get(indexOfDo) == doStatement &&
                    upperStatementStats.get(indexOfDo - 1) instanceof CatchStatement catchStatement &&
                    catchStatement.getStats().size() >= 2 &&
                    catchStatement.getStats().get(1).getExprents() != null &&
                    !Objects.requireNonNull(catchStatement.getStats().get(1).getExprents()).isEmpty()) {
            firstExprents = catchStatement.getStats().get(1).getExprents();
            doParentStatement = doStatement;
            first = catchStatement.getStats().get(1);
          } else {
            return null;
          }
        }
        else {
          return null;
        }
      }

      List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();

      Exprent nonNullCheck = null;
      AssignmentExprent initVar2 = null;
      if (firstExprents == null) {
        return null;
      }
      for (Exprent exprent : firstExprents) {
        if (exprent instanceof AssignmentExprent firstAssignment && instance.equals(firstAssignment.getLeft())) {
          instance = firstAssignment.getRight();
          if (firstAssignment.getLeft() instanceof VarExprent varExprent) {
            varExprent.setDefinition(true);
            tempVarAssignments.add(new TempVarAssignmentItem(varExprent, first));
          }
        }
      }
      for (Exprent exprent : firstExprents) {
        if (isNonNullCheck(exprent, instance)) {
          nonNullCheck = exprent;
        }
        if (exprent instanceof AssignmentExprent firstAssignment && typeVar.equals(firstAssignment.getLeft()) &&
            firstAssignment.getRight() instanceof ConstExprent constExprent &&
            constExprent.getValue() instanceof Integer value &&
            value == 0) {
          initVar2 = firstAssignment;

          if (firstAssignment.getLeft() instanceof VarExprent varExprent) {
            varExprent.setDefinition(true);
            tempVarAssignments.add(new TempVarAssignmentItem(varExprent, first));
          }
        }
      }

      if (initVar2 == null) {
        return null;
      }

      if (statement.getCaseValues().size() != statement.getCaseEdges().size() ||
          statement.getCaseValues().size() != statement.getCaseStatements().size()) {
        return null;
      }

      HashSet<AssignmentExprent> usedTypeVarAssignments = new HashSet<>();
      usedTypeVarAssignments.add(initVar2);
      GuardPatternContainer guardPatternContainer = null;
      if (doParentStatement != null) {
        guardPatternContainer = collectGuards(statement, typeVar, doParentStatement, usedTypeVarAssignments, tempVarAssignments);
      }
      if (reInitVar(typeVar, statement, usedTypeVarAssignments)) {
        return null;
      }


      List<FullCase> resortedCases = resortForSwitchBootstrap(statement, nonNullCheck != null);
      if (resortedCases == null) {
        return null;
      }

      List<Exprent> finalFirstExprents = firstExprents;
      Exprent finalNonNullCheck = nonNullCheck;

      return new SwitchOnReferenceCandidate(statement, switchSelector, instance, tempVarAssignments, resortedCases, guardPatternContainer,
                                            () -> {
                                              if (finalNonNullCheck != null) {
                                                finalFirstExprents.remove(finalNonNullCheck);
                                              }
                                            });
    }

    private static GuardPatternContainer collectGuards(@NotNull SwitchStatement statement,
                                                       @NotNull Exprent typeVar,
                                                       @NotNull Statement doParentStatement,
                                                       @NotNull Set<AssignmentExprent> usedAssignments,
                                                       @NotNull List<TempVarAssignmentItem> tempVarAssignments) {
      GuardPatternContainer container = new GuardPatternContainer();
      @NotNull List<Statement> statements = statement.getCaseStatements();
      for (int i = 0; i < statements.size(); i++) {
        Statement caseStatement = statements.get(i);
        if (caseStatement.getStats().size() < 2) {
          continue;
        }
        List<@Nullable Exprent> exprents = statement.getCaseValues().get(i);
        OptionalInt maxCaseValue = exprents.stream().filter(t -> t instanceof ConstExprent)
          .mapToInt(t -> ((ConstExprent)t).getIntValue())
          .max();
        if (maxCaseValue.isEmpty()) {
          continue;
        }
        boolean negated = false;

        if (!(caseStatement.getStats().get(0) instanceof IfStatement ifStatement &&
              ifStatement.getIfstat() != null &&
              ifStatement.getElsestat() == null &&
              ifStatement.iftype == IFTYPE_IF)) {
          continue;
        }
        AssignmentExprent assignmentExprent = null;

        if (ifStatement.getIfstat().getExprents()!=null &&
            ifStatement.getIfstat().getExprents().size()==1 &&
            ifStatement.getIfstat().getExprents().get(0) instanceof AssignmentExprent expectedAssignmentExprent &&
            expectedAssignmentExprent.getLeft() != typeVar && expectedAssignmentExprent.getRight() instanceof ConstExprent constExprent &&
                      constExprent.getValue() instanceof Integer index && index > maxCaseValue.getAsInt()) {
          assignmentExprent = expectedAssignmentExprent;
          negated = true;
        }

        if (!negated) {
          Statement breakStatement = caseStatement.getStats().get(1);
          Statement expectedBreakStatement = caseStatement.getStats().get(caseStatement.getStats().size() - 1);
          List<StatEdge> successorEdges = expectedBreakStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
          if (successorEdges.size() != 1) {
            continue;
          }
          StatEdge breakEdge = successorEdges.get(0);
          if (breakEdge.getType() != EdgeType.BREAK) {
            continue;
          }
          if (breakEdge.getDestination() != doParentStatement) {
            continue;
          }
          if (!(breakStatement.getExprents() != null &&
                breakStatement.getExprents().size() == 1 &&
                breakStatement.getExprents().get(0) instanceof AssignmentExprent expectedAssignmentExprent &&
                expectedAssignmentExprent.getLeft() != typeVar &&
                expectedAssignmentExprent.getRight() instanceof ConstExprent constExprent &&
                constExprent.getValue() instanceof Integer index &&
                index > maxCaseValue.getAsInt())) {
            continue;
          }
          assignmentExprent = expectedAssignmentExprent;
          Statement ifstat = ifStatement.getIfstat();
          List<StatEdge> edges = ifstat.getSuccessorEdges(EdgeType.DIRECT_ALL);
          if (!edges.isEmpty() && (edges.size() != 1 || edges.get(0).getType() == EdgeType.REGULAR)) {
            continue;
          }
        }

        if (!(ifStatement.getHeadexprentList().size() == 1 &&
              ifStatement.getHeadexprent() != null &&
              ifStatement.getHeadexprent().getCondition() != null &&
              ifStatement.getIfstat() != null)) {
          continue;
        }

        //now only guards
        IfExprent ifExprent = ifStatement.getHeadexprent();
        Statement newCaseStatement = ifStatement.getIfstat();
        if (negated) {
          ifExprent = ifExprent.negateIf();
          newCaseStatement = new SequenceStatement(caseStatement.getStats().subList(1, caseStatement.getStats().size()));
        }
        Exprent nameAssignment = Optional.ofNullable(ifStatement.getStats())
          .map(stats -> !stats.isEmpty() ? stats.get(0).getExprents() : null)
          .map(exprs -> exprs.size() == 1 ? exprs.get(0) : null)
          .orElse(null);
        container.addGuard(caseStatement, ifExprent.getCondition(), newCaseStatement, nameAssignment);

        usedAssignments.add(assignmentExprent);
        if (nameAssignment instanceof AssignmentExprent nameAssignmentExprent &&
            nameAssignmentExprent.getLeft() instanceof VarExprent varExprent) {
          tempVarAssignments.add(new TempVarAssignmentItem(varExprent, ifStatement));
        }
      }
      return container;
    }

    private static boolean isNonNullCheck(@NotNull Exprent exprent, @NotNull Exprent switchVar) {
      if (!(exprent instanceof InvocationExprent invocationExprent)) {
        return false;
      }
      if (!invocationExprent.isStatic() || !"requireNonNull".equals(invocationExprent.getName()) ||
          !JAVA_LANG_OBJECT.equals(invocationExprent.getClassName()) ||
          invocationExprent.getParameters() == null ||
          invocationExprent.getParameters().size() != 1 ||
          !switchVar.equals(invocationExprent.getParameters().get(0))) {
        return false;
      }
      return true;
    }

    private static boolean reInitVar(@NotNull Exprent var, @NotNull Statement statement, @NotNull Set<AssignmentExprent> exclude) {
      List<Exprent> exprents = statement.getExprents();
      if (exprents != null) {
        for (Exprent exprent : exprents) {
          if (!(exprent instanceof AssignmentExprent assignmentExprent)) {
            continue;
          }
          if (exclude.contains(assignmentExprent)) {
            continue;
          }
          Exprent left = assignmentExprent.getLeft();
          if (var.equals(left)) {
            return true;
          }
        }
      }
      if (statement.getStats() != null) {
        for (Statement children : statement.getStats()) {
          if (reInitVar(var, children, exclude)) {
            return true;
          }
        }
      }
      return false;
    }

    private static class SwitchOnReferenceCandidate implements SwitchOnCandidate {
      @NotNull
      private final SwitchStatement statement;
      @NotNull
      private final InvocationExprent switchSelector;
      private final Exprent newSwitchSelector;
      @NotNull
      private final List<TempVarAssignmentItem> tempVarAssignments;
      @NotNull
      private final List<FullCase> sortedCases;
      @Nullable
      private final Runnable cleaner;
      @Nullable
      private final GuardPatternContainer guardPatternContainer;

      private SwitchOnReferenceCandidate(@NotNull SwitchStatement statement,
                                         @NotNull InvocationExprent selector,
                                         @NotNull Exprent newSelector,
                                         @NotNull List<TempVarAssignmentItem> tempVarAssignments,
                                         @NotNull List<FullCase> cases,
                                         @Nullable GuardPatternContainer guardPatternContainer,
                                         @Nullable Runnable cleaner) {
        this.statement = statement;
        this.switchSelector = selector;
        this.newSwitchSelector = newSelector;
        this.tempVarAssignments = tempVarAssignments;
        this.sortedCases = cases;
        this.cleaner = cleaner;
        this.guardPatternContainer = guardPatternContainer;
      }

      @Override
      public void simplify(List<TempVarAssignmentItem> tempVarAssignments) {
        if (cleaner != null) {
          cleaner.run();
        }
        resort(statement, sortedCases);
        Exprent headExprent = statement.getHeadExprent();
        List<PooledConstant> bootstrapArguments = switchSelector.getBootstrapArguments();
        Map<Integer, Exprent> mapCaseValue = new HashMap<>();
        Map<Integer, String> mapCaseClasses = new HashMap<>();
        for (int i = 0; i < bootstrapArguments.size(); i++) {
          PooledConstant constant = bootstrapArguments.get(i);
          if (constant instanceof PrimitiveConstant primitiveConstant) {
            if (primitiveConstant.type == CONSTANT_Class) {
              mapCaseClasses.put(i, primitiveConstant.getString());
            }
            else if (switchSelector.isDynamicCall("enumSwitch", 2)) {
              mapCaseValue.put(i, new FieldExprent(primitiveConstant.getString(), null, true, null, null, null));
            }
            else {
              VarType type = switch (primitiveConstant.type) {
                case CONSTANT_String -> VARTYPE_STRING;
                case CONSTANT_Integer -> VARTYPE_INT;
                default -> VARTYPE_UNKNOWN;
              };
              mapCaseValue.put(i, new ConstExprent(type, primitiveConstant.value, null));
            }
          }
        }
        @NotNull List<List<@Nullable Exprent>> values = statement.getCaseValues();
        boolean hasPattern = false;
        for (int caseIndex = 0; caseIndex < values.size(); caseIndex++) {
          List<Exprent> caseValues = values.get(caseIndex);
          for (int valueIndex = 0; valueIndex < caseValues.size(); valueIndex++) {
            Exprent caseValue = caseValues.get(valueIndex);
            if (!(caseValue instanceof ConstExprent constCaseValue)) {
              continue;
            }
            int expectedValue = constCaseValue.getIntValue();
            if (expectedValue == -1) {
              caseValues.set(valueIndex, new ConstExprent(VARTYPE_NULL, null, null));
            }
            Exprent newCaseValue = mapCaseValue.get(expectedValue);
            if (newCaseValue == null) {
              String className = mapCaseClasses.get(expectedValue);
              if (className == null) {
                continue;
              }
              newCaseValue = preparePatternVar(className,
                                               statement.getCaseStatements().get(caseIndex),
                                               switchSelector.getInstance(),
                                               guardPatternContainer,
                                               this.tempVarAssignments);
              hasPattern = true;
            }
            caseValues.set(valueIndex, newCaseValue);
          }
        }

        if (headExprent != null) {
          headExprent.replaceExprent(switchSelector, newSwitchSelector);
        }

        if (hasPattern) {
          remapWithGuards(statement, guardPatternContainer);
          cleanDefault(statement);
        }
        tempVarAssignments.addAll(this.tempVarAssignments);
      }

      private static void remapWithGuards(@NotNull SwitchStatement statement, @Nullable GuardPatternContainer guardPatternContainer) {
        if (guardPatternContainer == null) {
          return;
        }
        for (int i = 0; i < statement.getCaseStatements().size(); i++) {
          Statement currentCaseStatement = statement.getCaseStatements().get(i);
          GuardPatternContainer.GuardStatement guards = guardPatternContainer.getGuards(currentCaseStatement);
          if (guards == null) {
            continue;
          }
          statement.replaceStatement(statement.getCaseStatements().get(i), guards.caseStatement());
          statement.getCaseStatements().set(i, guards.caseStatement());
          statement.getCaseEdges().get(i).forEach(edge -> {
                                                    edge.setDestination(guards.caseStatement);
                                                  }
          );
          statement.addGuard(statement.getCaseStatements().get(i), guards.guard());
        }
        Statement expectedDoStatement = statement.getParent();
        if (expectedDoStatement instanceof DoStatement) {
          expectedDoStatement.getParent().replaceStatement(expectedDoStatement, statement);
        }
        else if (expectedDoStatement.getParent() instanceof DoStatement doStatement) {
          doStatement.getParent().replaceStatement(doStatement, expectedDoStatement);
        }
        @NotNull List<Statement> statements = statement.getCaseStatements();
        for (int i = 0; i < statements.size(); i++) {
          Statement caseStatement = statements.get(i);
          List<StatEdge> edges = caseStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
          if (edges.isEmpty() && !caseStatement.getStats().isEmpty()) {
            edges = caseStatement.getStats().get(caseStatement.getStats().size() - 1).getSuccessorEdges(EdgeType.DIRECT_ALL);
          }
          if (edges.size() == 1 && edges.get(0).getType() == EdgeType.BREAK &&
              (expectedDoStatement.equals(edges.get(0).closure) || statement.equals(edges.get(0).closure))) {
            edges.get(0).labeled = false;
          }
        }
      }

      private static void cleanDefault(@NotNull SwitchStatement statement) {
        @NotNull List<List<StatEdge>> caseEdges = statement.getCaseEdges();
        int indexDefault = -1;
        boolean deleteDefault = false;
        for (int caseIndex = 0; caseIndex < caseEdges.size(); caseIndex++) {
          List<StatEdge> edges = caseEdges.get(caseIndex);
          int indexDefaultInside = edges.indexOf(statement.getDefaultEdge());
          if (indexDefaultInside == -1) {
            continue;
          }
          indexDefault = caseIndex;
          //delete other case values for default
          if (edges.size() != 1) {
            List<Exprent> exprentsToRemove = new ArrayList<>();
            List<StatEdge> edgesToRemove = new ArrayList<>();
            List<@Nullable Exprent> exprents = statement.getCaseValues().get(caseIndex);
            for (int i = 0; i < edges.size(); i++) {
              if (edges.get(i) == statement.getDefaultEdge()) {
                continue;
              }
              if (exprents.get(i) instanceof VarExprent) {
                exprentsToRemove.add(exprents.get(i));
                edgesToRemove.add(edges.get(i));
              }
            }
            edges.removeAll(edgesToRemove);
            exprents.removeAll(exprentsToRemove);

            break;
          }
          Statement defaultStatement = statement.getCaseStatements().get(caseIndex);
          if (defaultStatement.getExprents() != null && defaultStatement.getExprents().size() == 1) {
            Exprent expectedFastExit = defaultStatement.getExprents().get(0);
            if (expectedFastExit instanceof ExitExprent exitExprent && exitExprent.getExitType() == EXIT_THROW &&
                exitExprent.getValue() instanceof NewExprent newExprent && newExprent.getConstructor() != null) {
              InvocationExprent constructor = newExprent.getConstructor();
              if ("java/lang/MatchException".equals(constructor.getClassName()) && constructor.getParameters().size() == 2 &&
                  constructor.getParameters().get(0) instanceof ConstExprent constExprent1 && constExprent1.isNull() &&
                  constructor.getParameters().get(1) instanceof ConstExprent constExprent2 && constExprent2.isNull()) {
                deleteDefault = true;
              }
            }
          }
          break;
        }
        if (deleteDefault) {
          statement.getCaseStatements().remove(indexDefault);
          statement.getCaseValues().remove(indexDefault);
          statement.getCaseEdges().remove(indexDefault);
        }
      }

      @NotNull
      private static Exprent preparePatternVar(@NotNull String className,
                                               @NotNull Statement statement,
                                               @NotNull Exprent switchVar,
                                               @Nullable GuardPatternContainer guardPatternContainer,
                                               @NotNull List<TempVarAssignmentItem> tempVarAssignments) {
        Exprent expectedAssignment = null;
        if (guardPatternContainer != null) {
          GuardPatternContainer.GuardStatement guard = guardPatternContainer.getGuards(statement);
          if (guard != null) {
            expectedAssignment = guard.nameAssignment();
          }
        }
        Map<List<Exprent>, Statement> expectedAssignmentExprents = null;
        if (expectedAssignment == null) {
          expectedAssignmentExprents = tryFindFirstAssignment(statement);

          if (expectedAssignmentExprents.isEmpty()) {
            return createDefaultPatternVal(className);
          }
          expectedAssignment = expectedAssignmentExprents.entrySet().iterator().next().getKey().get(0);
        }
        if (!(expectedAssignment instanceof AssignmentExprent assignmentExprent)) {
          return createDefaultPatternVal(className);
        }
        Exprent right = assignmentExprent.getRight();
        if (!(right instanceof FunctionExprent functionExprent)) {
          return createDefaultPatternVal(className);
        }
        if (functionExprent.getFuncType() != FUNCTION_CAST || functionExprent.getLstOperands() == null ||
            functionExprent.getLstOperands().size() != 2 ||
            !(functionExprent.getLstOperands().get(1) instanceof ConstExprent classCast) ||
            !(functionExprent.getLstOperands().get(0) instanceof VarExprent varName)) {
          return createDefaultPatternVal(className);
        }

        if (!className.equals(classCast.getConstType().getValue()) || !switchVar.equals(varName)) {
          return createDefaultPatternVal(className);
        }

        if (expectedAssignmentExprents != null) {
          Map.Entry<List<Exprent>, Statement> listStatementEntry = expectedAssignmentExprents.entrySet().iterator().next();
          Exprent removed = listStatementEntry.getKey().remove(0);
          if (removed instanceof AssignmentExprent assignment && assignment.getLeft() instanceof VarExprent toDelete) {
            toDelete.setDefinition(true);
            tempVarAssignments.add(new TempVarAssignmentItem(toDelete, listStatementEntry.getValue()));
          }
        }
        Exprent left = assignmentExprent.getLeft();
        if (!(left instanceof VarExprent varExprent)) {
          return createDefaultPatternVal(className);
        }
        VarExprent copy = (VarExprent)varExprent.copy();
        copy.setDefinition(true);
        return copy;
      }

      @NotNull
      private static Map<List<Exprent>, Statement> tryFindFirstAssignment(@NotNull Statement statement) {
        List<Exprent> exprents = statement.getExprents();
        if (exprents == null || exprents.isEmpty()) {
          if (statement instanceof IfStatement || statement instanceof SequenceStatement) {
            return tryFindFirstAssignment(statement.getFirst());
          }
          return new HashMap<>();
        }
        HashMap<List<Exprent>, Statement> result = new HashMap<>();
        result.put(exprents, statement);
        return result;
      }

      @NotNull
      private static Exprent createDefaultPatternVal(@NotNull String name) {
        VarProcessor processor = DecompilerContext.getVarProcessor();
        VarExprent varExprent = new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                               new VarType(TYPE_OBJECT, 0, name),
                                               processor);
        varExprent.setDefinition(true);
        processor.setVarName(varExprent.getVarVersionPair(), VarExprent.getName(varExprent.getVarVersionPair()));
        return varExprent;
      }


      @SuppressWarnings({"SSBasedInspection", "SimplifyStreamApiCallChains"})
      private static void resort(@NotNull SwitchStatement statement, @NotNull List<FullCase> cases) {
        statement.getCaseStatements().clear();
        statement.getCaseStatements().addAll(cases.stream().map(t -> t.statement).collect(Collectors.toList()));
        statement.getCaseEdges().clear();
        statement.getCaseEdges().addAll(cases.stream().map(t -> t.edges).collect(Collectors.toList()));
        statement.getCaseValues().clear();
        statement.getCaseValues().addAll(cases.stream().map(t -> t.exprents).collect(Collectors.toList()));
      }
    }
  }

  private static class GuardPatternContainer {
    private final Map<Statement, GuardStatement> guards = new HashMap<>();

    record GuardStatement(@NotNull Statement statement,
                          @NotNull Exprent guard,
                          @NotNull Statement caseStatement,
                          @Nullable Exprent nameAssignment) {
    }

    void addGuard(@NotNull Statement statement,
                  @NotNull Exprent guard,
                  @NotNull Statement caseStatement,
                  @Nullable Exprent nameAssignment) {
      guards.put(statement, new GuardStatement(statement, guard, caseStatement, nameAssignment));
    }

    GuardStatement getGuards(@NotNull Statement statement) {
      return guards.get(statement);
    }
  }

  private abstract static class StringSwitchRecognizer implements SwitchRecognizer {

    @Override
    @Nullable
    public abstract SwitchOnStringCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector);

    @NotNull
    Set<Object> findRealCaseValuesHashCodes(@NotNull SwitchStatement switchStatement) {
      // noinspection SSBasedInspection
      return switchStatement.getCaseValues().stream()
        // We take only buckets that don't contain null value.
        // null value represents default branch and no temp variable is assigned there.
        // Also, the bucket with null value may also contain fictive case values,
        // generated for tableswitch instruction (see com.sun.tools.javac.jvm.Gen.visitSwitch)
        .filter(values -> values.stream().noneMatch(Objects::isNull)).flatMap(Collection::stream)
        .map(value -> ((ConstExprent)value)).map(ConstExprent::getValue)
        .collect(Collectors.toSet());
    }

    @Nullable
    String findRealCaseValue(@NotNull IfStatement ifStatement, @NotNull Exprent selectorQualifier) {
      Exprent ifCondition = ifStatement.getHeadexprent().getCondition();
      if (ifCondition.type != Exprent.EXPRENT_INVOCATION) return null;
      InvocationExprent invocationCondition = (InvocationExprent)ifCondition;
      if (!invocationCondition.isInstanceCall(ClassNameConstants.JAVA_LANG_STRING, "equals", 1)) return null;
      if (!invocationCondition.getInstance().equals(selectorQualifier)) return null;
      Exprent equalsParameter = invocationCondition.getParameters().get(0);
      if (equalsParameter.type != Exprent.EXPRENT_CONST) return null;
      Object caseLabelValue = ((ConstExprent)equalsParameter).getValue();
      // We take hash code of case label value for comparing, as javac uses this strategy to generate the first switch statement.
      // Seems Ecj uses the same strategy.
      //
      // From javac doc:
      // The generated code assumes that the hashing algorithm
      // of String is the same in the compilation environment as
      // in the environment the code will run in.  The string
      // hashing algorithm in the SE JDK has been unchanged
      // since at least JDK 1.2.  Since the algorithm has been
      // specified since that release as well, it is very
      // unlikely to be changed in the future.
      return caseLabelValue instanceof String ? (String)caseLabelValue : null;
    }

    private static class JavacStringRecognizer extends StringSwitchRecognizer {
      /**
       * From javac doc:
       * The general approach used is to translate a single
       * string switch statement into a series of two chained
       * switch statements: the first a synthesized statement
       * switching on the argument string's hash value and
       * computing a string's position in the list of original
       * case labels, if any, followed by a second switch on the
       * computed integer value.  The second switch has the same
       * code structure as the original string switch statement
       * except that the string case labels are replaced with
       * positional integer constants starting at 0.
       *
       * @param firstSwitch    outer or first switch
       * @param switchSelector <code>firstSwitch</code> selector
       * @return recognized switch candidate
       * @see Lower#visitStringSwitch(JCTree.JCSwitch)
       */
      @Nullable
      @Override
      public SwitchOnStringCandidate recognize(@NotNull SwitchStatement firstSwitch,
                                               @NotNull InvocationExprent switchSelector) {
        if (switchSelector.getInstance().type != Exprent.EXPRENT_VAR) return null;
        if (!switchSelector.isInstanceCall(ClassNameConstants.JAVA_LANG_STRING, "hashCode", 0)) return null;

        Set<Object> realCaseValueHashCodes = findRealCaseValuesHashCodes(firstSwitch);
        VarExprent firstSwitchSelectorQualifier = (VarExprent)switchSelector.getInstance();
        VarExprent tmpVarAssignTo = null;
        SwitchStatement secondSwitch = null;
        Map<Integer, String> mappedCaseLabelValues = new HashMap<>();
        for (Statement statement : firstSwitch.getCaseStatements()) {
          if (statement.type != StatementType.IF) {
            Statement defaultStatement = firstSwitch.getDefaultEdge().getDestination();
            if (defaultStatement != statement) return null;
            continue;
          }
          IfStatement ifStatement = (IfStatement)statement;
          String caseLabelValue = findRealCaseValue(ifStatement, firstSwitchSelectorQualifier);
          if (caseLabelValue == null) return null;
          if (!realCaseValueHashCodes.remove(caseLabelValue.hashCode())) return null;
          if (ifStatement.getIfstat() == null) return null;
          List<Exprent> ifStatementExprents = ifStatement.getIfstat().getExprents();
          if (ifStatementExprents == null || ifStatementExprents.size() != 1) return null;
          if (ifStatementExprents.get(0).type != Exprent.EXPRENT_ASSIGNMENT) return null;
          AssignmentExprent assignment = (AssignmentExprent)ifStatementExprents.get(0);
          if (assignment.getLeft().type != Exprent.EXPRENT_VAR || assignment.getRight().type != Exprent.EXPRENT_CONST) return null;
          // tmp variable should be the same for all assignments
          if (tmpVarAssignTo != null && !tmpVarAssignTo.equals(assignment.getLeft())) return null;
          tmpVarAssignTo = (VarExprent)assignment.getLeft();
          Object valueAssignedToTmpVar = ((ConstExprent)assignment.getRight()).getValue();
          if (!(valueAssignedToTmpVar instanceof Integer)) return null;
          mappedCaseLabelValues.put((Integer)valueAssignedToTmpVar, caseLabelValue);

          if (ifStatement.getLabelEdges().size() != 1) return null;
          Statement edgeDestination = ifStatement.getLabelEdges().iterator().next().getDestination();
          if (edgeDestination.type == StatementType.SEQUENCE) {
            edgeDestination = edgeDestination.getFirst();
          }
          if (edgeDestination.type != StatementType.SWITCH) return null;
          // the switch should be the same for all case labels
          if (secondSwitch != null && secondSwitch != edgeDestination) return null;
          secondSwitch = (SwitchStatement)edgeDestination;
        }
        if (secondSwitch == null || !realCaseValueHashCodes.isEmpty()) return null;

        Exprent siblingSwitchExprent = secondSwitch.getHeadExprent();
        if (siblingSwitchExprent == null) return null;
        if (siblingSwitchExprent.type != Exprent.EXPRENT_SWITCH) return null;
        if (!tmpVarAssignTo.equals(((SwitchExprent)siblingSwitchExprent).getValue())) return null;
        return new SwitchOnStringCandidate.JavacSwitchCandidate(firstSwitch, firstSwitchSelectorQualifier, tmpVarAssignTo, secondSwitch,
                                                                mappedCaseLabelValues);
      }
    }

    private static class EcjStringRecognizer extends StringSwitchRecognizer {
      @Nullable
      @Override
      public SwitchOnStringCandidate recognize(@NotNull SwitchStatement switchStatement, @NotNull InvocationExprent switchSelector) {
        if (!switchSelector.isInstanceCall(ClassNameConstants.JAVA_LANG_STRING, "hashCode", 0)) return null;
        Set<Object> realCaseValueHashCodes = findRealCaseValuesHashCodes(switchStatement);
        Exprent switchSelectorQualifier = switchSelector.getInstance();
        Map<Integer, String> mappedCaseLabelValues = new HashMap<>();
        Map<Integer, IfStatement> ifBodyStatements = new HashMap<>();
        VarExprent tempVar = null;
        for (Statement statement : switchStatement.getCaseStatements()) {
          if (statement.type != StatementType.IF) {
            Statement defaultStatement = switchStatement.getDefaultEdge().getDestination();
            if (defaultStatement != statement) return null;
            continue;
          }
          Exprent tempSwitchSelectorQualifier = switchSelectorQualifier;
          if (switchSelectorQualifier.type == Exprent.EXPRENT_ASSIGNMENT) {
            tempSwitchSelectorQualifier = ((AssignmentExprent)switchSelectorQualifier).getLeft();
          }
          else if (switchSelectorQualifier.type == Exprent.EXPRENT_CONST && switchStatement.getFirst().getExprents() != null) {
            // Ecj inlines compile constants, so we try to find a temp variable that is assigned to the same const value.
            // It should be in the first basic block.
            Exprent finalSwitchSelectorQualifier = switchSelectorQualifier;
            tempSwitchSelectorQualifier = switchStatement.getFirst().getExprents().stream()
              .filter(exprent -> exprent instanceof AssignmentExprent)
              .map(exprent -> (AssignmentExprent)exprent)
              .filter(exprent -> exprent.getRight().equals(finalSwitchSelectorQualifier))
              .map(AssignmentExprent::getLeft)
              .findFirst()
              .orElse(null);
            if (tempSwitchSelectorQualifier == null) return null;
          }
          if (tempVar != null && !tempVar.equals(tempSwitchSelectorQualifier)) return null;
          tempVar = (VarExprent)tempSwitchSelectorQualifier;
          IfStatement ifStatement = (IfStatement)statement;
          String caseLabelValue = findRealCaseValue(ifStatement, tempVar);
          if (caseLabelValue == null) return null;
          int caseLabelHash = caseLabelValue.hashCode();
          if (!realCaseValueHashCodes.remove(caseLabelHash)) return null;
          mappedCaseLabelValues.put(caseLabelHash, caseLabelValue);
          ifBodyStatements.put(caseLabelHash, ifStatement);
        }
        if (tempVar == null || !realCaseValueHashCodes.isEmpty()) return null;
        if (switchSelectorQualifier instanceof AssignmentExprent) {
          switchSelectorQualifier = ((AssignmentExprent)switchSelectorQualifier).getRight();
        }
        return new SwitchOnStringCandidate.EcjSwitchCandidate(switchStatement, switchSelectorQualifier, tempVar, ifBodyStatements,
                                                              mappedCaseLabelValues);
      }
    }
  }

  private interface SwitchOnCandidate {
    void simplify(List<TempVarAssignmentItem> tempVarAssignments);
  }

  private abstract static class SwitchOnStringCandidate implements SwitchOnCandidate {
    @Nullable
    private static AssignmentExprent addTempVarAssignment(@NotNull Exprent exprent,
                                                          @NotNull VarExprent varExprent,
                                                          @NotNull Statement statement,
                                                          @NotNull List<TempVarAssignmentItem> tempVarAssignments) {
      if (exprent.type != Exprent.EXPRENT_ASSIGNMENT) return null;
      AssignmentExprent assignment = (AssignmentExprent)exprent;
      if (!varExprent.isDefinition() && varExprent.equals(assignment.getLeft())) {
        tempVarAssignments.add(new TempVarAssignmentItem(varExprent, statement));
        return assignment;
      }
      return null;
    }

    private static class JavacSwitchCandidate extends SwitchOnStringCandidate {
      @NotNull private final SwitchStatement firstSwitch;
      @NotNull private final VarExprent firstSwitchSelector;
      @NotNull private final VarExprent tmpVarInFirstSwitch;
      @NotNull private final SwitchStatement secondSwitch;
      @NotNull private final Map<Integer, @NotNull String> mappedCaseLabelValues;

      JavacSwitchCandidate(@NotNull SwitchStatement firstSwitch,
                           @NotNull VarExprent firstSwitchSelector,
                           @NotNull VarExprent tmpVarInFirstSwitch,
                           @NotNull SwitchStatement secondSwitch,
                           @NotNull Map<Integer, String> mappedCaseLabelValues) {
        this.firstSwitch = firstSwitch;
        this.secondSwitch = secondSwitch;
        this.firstSwitchSelector = firstSwitchSelector;
        this.tmpVarInFirstSwitch = tmpVarInFirstSwitch;
        this.mappedCaseLabelValues = mappedCaseLabelValues;
      }

      @Override
      public void simplify(@NotNull List<TempVarAssignmentItem> tempVarAssignments) {
        Exprent secondSwitchSelector = secondSwitch.getHeadExprent();
        if (secondSwitchSelector == null || secondSwitchSelector.type != Exprent.EXPRENT_SWITCH) return;
        Statement firstStatementInFirstSwitch = firstSwitch.getFirst();
        if (firstStatementInFirstSwitch.type != StatementType.BASIC_BLOCK) return;
        Statement firstStatementInSecondSwitch = secondSwitch.getFirst();
        if (firstStatementInSecondSwitch.type != StatementType.BASIC_BLOCK) return;

        for (List<Exprent> values : secondSwitch.getCaseValues()) {
          for (int i = 0; i < values.size(); i++) {
            ConstExprent constExprent = (ConstExprent)values.get(i);
            if (constExprent == null) continue;
            String labelValue = mappedCaseLabelValues.get(constExprent.getIntValue());
            values.set(i, new ConstExprent(VARTYPE_STRING, labelValue, null));
          }
        }

        List<Exprent> firstSwitchExprents = firstStatementInFirstSwitch.getExprents();
        if (firstSwitchExprents == null || firstStatementInSecondSwitch.getExprents() == null) return;
        int lastExprentIndex = firstSwitchExprents.size();
        if (lastExprentIndex > 0) {
          AssignmentExprent assignment = addTempVarAssignment(firstSwitchExprents.get(lastExprentIndex - 1), tmpVarInFirstSwitch,
                                                              secondSwitch, tempVarAssignments);
          if (assignment != null) {
            lastExprentIndex--;
          }
        }
        Exprent newSelector = firstSwitchSelector;
        if (lastExprentIndex > 0) {
          AssignmentExprent assignment = addTempVarAssignment(firstSwitchExprents.get(lastExprentIndex - 1), firstSwitchSelector,
                                                              secondSwitch, tempVarAssignments);
          if (assignment != null) {
            lastExprentIndex--;
            newSelector = assignment.getRight();
          }
        }
        List<Exprent> newExprents;
        if (lastExprentIndex > 0) {
          newExprents = List.copyOf(firstSwitchExprents.subList(0, lastExprentIndex));
        }
        else {
          newExprents = Collections.emptyList();
        }
        Statement firstSwitchParent = firstSwitch.getParent();
        Statement secondSwitchParent = secondSwitch.getParent();
        boolean parentsAreTheSame = firstSwitchParent == secondSwitchParent;
        if (parentsAreTheSame || secondSwitchParent == firstSwitch) {
          firstSwitchParent.replaceStatement(firstSwitch, secondSwitch);
        }
        else if (secondSwitchParent.getParent() == firstSwitch) {
          firstSwitchParent.replaceStatement(firstSwitch, secondSwitchParent);
        }
        if (parentsAreTheSame) {
          firstSwitchParent.getStats().removeWithKey(secondSwitch.id);
        }
        firstStatementInSecondSwitch.getExprents().addAll(0, newExprents);
        secondSwitchSelector.replaceExprent(((SwitchExprent)secondSwitchSelector).getValue(), newSelector);
      }
    }

    private static class EcjSwitchCandidate extends SwitchOnStringCandidate {
      @NotNull private final SwitchStatement switchStatement;
      @NotNull private final Exprent switchSelector;
      @NotNull private final VarExprent tmpVar;
      @NotNull private final Map<Integer, @NotNull IfStatement> mappedIfStatements;
      @NotNull private final Map<Integer, @NotNull String> mappedCaseLabelValues;

      private EcjSwitchCandidate(@NotNull SwitchStatement switchStatement,
                                 @NotNull Exprent switchSelector,
                                 @NotNull VarExprent tmpVar,
                                 @NotNull Map<Integer, IfStatement> mappedIfStatements,
                                 @NotNull Map<Integer, String> mappedCaseLabelValues) {
        this.switchStatement = switchStatement;
        this.switchSelector = switchSelector;
        this.tmpVar = tmpVar;
        this.mappedIfStatements = mappedIfStatements;
        this.mappedCaseLabelValues = mappedCaseLabelValues;
      }

      @Override
      public void simplify(@NotNull List<TempVarAssignmentItem> tempVarAssignments) {
        Exprent switchSelector = switchStatement.getHeadExprent();
        if (switchSelector == null || switchSelector.type != Exprent.EXPRENT_SWITCH) return;
        tempVarAssignments.add(new TempVarAssignmentItem(tmpVar, switchStatement));
        for (List<Exprent> values : switchStatement.getCaseValues()) {
          for (int i = 0; i < values.size(); i++) {
            ConstExprent constExprent = (ConstExprent)values.get(i);
            if (constExprent == null) continue;
            int caseLabelHash = constExprent.getIntValue();
            String labelValue = mappedCaseLabelValues.get(caseLabelHash);
            values.set(i, new ConstExprent(VARTYPE_STRING, labelValue, null));
            IfStatement ifStatement = mappedIfStatements.get(caseLabelHash);
            assert !ifStatement.getStats().isEmpty();
            if (ifStatement.getStats().size() == 1) {
              ifStatement.getParent().replaceStatement(ifStatement, ifStatement.getStats().get(0));
              continue;
            }
            removeOuterBreakEdge(ifStatement);
            ifStatement.getParent().replaceStatement(ifStatement, new SequenceStatement(ifStatement.getStats()));
          }
        }
        switchSelector.replaceExprent(((SwitchExprent)switchSelector).getValue(), this.switchSelector);
      }

      private static void removeOuterBreakEdge(@NotNull IfStatement ifStatement) {
        List<StatEdge> ifStatementBreakEdges = ifStatement.getSuccessorEdges(EdgeType.BREAK);
        if (ifStatementBreakEdges.size() != 1) return;
        Statement lastStatement = ifStatement.getStats().get(ifStatement.getStats().size() - 1);
        List<StatEdge> lastStatementBreakEdges = lastStatement.getSuccessorEdges(EdgeType.BREAK);
        if (lastStatementBreakEdges.size() != 1) return;
        StatEdge firstIfStatementBreakEdge = ifStatementBreakEdges.get(0);
        StatEdge lastStatementBreakEdge = lastStatementBreakEdges.get(0);
        if (firstIfStatementBreakEdge.getDestination() != lastStatementBreakEdge.getDestination()) {
          ifStatement.removeSuccessor(firstIfStatementBreakEdge);
        }
      }
    }
  }
}
