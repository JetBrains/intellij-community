// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.consts.PooledConstant;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jetbrains.java.decompiler.ClassNameConstants.JAVA_LANG_OBJECT;
import static org.jetbrains.java.decompiler.ClassNameConstants.JAVA_UTIL_OBJECTS;
import static org.jetbrains.java.decompiler.code.CodeConstants.*;
import static org.jetbrains.java.decompiler.modules.decompiler.PatternHelper.*;
import static org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper.SwitchOnCandidate;
import static org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper.TempVarAssignmentItem;
import static org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.EXIT_THROW;
import static org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.IFTYPE_IF;
import static org.jetbrains.java.decompiler.struct.gen.VarType.*;

@SuppressWarnings("SSBasedInspection")
public final class SwitchPatternHelper {

  public static boolean isBootstrapSwitch(@NotNull Exprent headExprent) {
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

  /**
   * A class that implements the SwitchHelper.SwitchRecognizer interface and provides a method to recognize a switch statement.
   *
   * @see JavacReferenceFinder for details
   */
  static class JavacReferenceRecognizer implements SwitchHelper.SwitchRecognizer {

    @Override
    public @Nullable SwitchOnCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector) {
      return new JavacReferenceFinder(null, new HashSet<>(), statement, switchSelector).findCandidate();
    }
  }

  /**
   * The JavacReferenceFinder class is responsible for finding reference candidates for a switch statement with patterns in bytecode.
   * This finder tries to find only javac familiar structures for patterns.
   * For deconstructions, only common structures are trying to be found; some cases are left
   */
  private static class JavacReferenceFinder {

    /**
     * The {@code FullCase} class represents a case in a switch statement. It contains the statement,
     * the list of expressions (exprents), and the list of statement edges related to the case.
     *
     * @see SwitchStatement
     */
    private record FullCase(@NotNull Statement statement, @NotNull List<Exprent> exprents, @NotNull List<StatEdge> edges) {
    }

    /**
     * Represents a case value with an associated edge from a switch statement
     *
     * @param exprent The expression value of the case.
     * @param edge    The edge associated with the case.
     * @see SwitchStatement
     */
    private record CaseValueWithEdge(@Nullable Exprent exprent, @Nullable StatEdge edge) {
    }


    /**
     * Represents the root DoStatement and exprents, which contain initializers.
     *
     * @param firstStatement    The firstStatement statement.
     * @param firstExprents     The list of expression statements.
     * @param doParentStatement The parent DoStatement object.
     */
    private record Root(Statement firstStatement, List<Exprent> firstExprents, DoStatement doParentStatement) {
    }

    /**
     * not null for nested switch
     */
    @Nullable
    private final VarTracker myVarTracker;

    /**
     * Represents a set of type variables, which is used as second parameter in bootstrap call.
     */
    @NotNull
    private final Set<Exprent> myTypeVars;
    @NotNull
    private final SwitchStatement myRootSwitchStatement;
    @NotNull
    private final InvocationExprent mySwitchSelector;

    @NotNull
    private final List<TempVarAssignmentItem> myTempVarAssignments = new ArrayList<>();

    @NotNull
    private final Set<SwitchStatement> myUsedSwitch = new HashSet<>();

    private JavacReferenceFinder(@Nullable VarTracker tracker,
                                 @NotNull Set<Exprent> typeVars,
                                 @NotNull SwitchStatement rootSwitchStatement,
                                 @NotNull InvocationExprent switchSelector) {
      this.myVarTracker = tracker;
      this.myTypeVars = new HashSet<>(typeVars);
      this.myRootSwitchStatement = rootSwitchStatement;
      this.mySwitchSelector = switchSelector;
      myUsedSwitch.add(myRootSwitchStatement);
    }

    public @Nullable SwitchOnReferenceCandidate findCandidate() {
      Exprent instance = mySwitchSelector.getInstance();
      if (instance == null) {
        return null;
      }
      if (myRootSwitchStatement.getHeadExprent() == null) {
        return null;
      }

      if (myRootSwitchStatement.getCaseValues().size() != myRootSwitchStatement.getCaseEdges().size() ||
          myRootSwitchStatement.getCaseValues().size() != myRootSwitchStatement.getCaseStatements().size()) {
        return null;
      }

      if (!(instance instanceof VarExprent instanceVarExprent)) return null;
      if (!isBootstrapSwitch(myRootSwitchStatement.getHeadExprent())) return null;
      if (checkBootstrap()) return null;

      List<Exprent> parameters = mySwitchSelector.getParameters();
      if (!instance.equals(parameters.get(0))) {
        return null;
      }
      Exprent typeVar = parameters.get(1);
      Root root = getRoot();
      if (root == null) return null;

      if (root.firstExprents() == null) {
        return null;
      }

      if (myVarTracker != null) {
        processAtLeastOneBlock(myVarTracker, root.firstStatement());
      }

      Initializer initializer = findInitializer(root, typeVar);

      if (initializer.initVar2() == null) {
        return null;
      }

      Set<AssignmentExprent> usedTypeVarAssignments = new HashSet<>();
      usedTypeVarAssignments.add(initializer.initVar2());
      myTypeVars.add(typeVar);

      Map<Statement, List<PatternVariableCandidate>> candidates =
        collectPatterns(instanceVarExprent, root.doParentStatement(), usedTypeVarAssignments);
      if (candidates == null) {
        return null;
      }
      candidates.values()
        .stream()
        .flatMap(t -> t.stream())
        .forEach(candidate -> myTempVarAssignments.addAll(candidate.getTempAssignments()));
      PatternContainer patternContainer = collectGuards(candidates, typeVar, root.doParentStatement(), usedTypeVarAssignments);
      if (root.doParentStatement() != null) {
        if (checkReinitVar(typeVar, root.doParentStatement(), usedTypeVarAssignments)) {
          return null;
        }
      }
      else {
        if (checkReinitVar(typeVar, myRootSwitchStatement, usedTypeVarAssignments)) {
          return null;
        }
      }


      List<FullCase> resortedCases = resortForSwitchBootstrap(myRootSwitchStatement);
      if (resortedCases == null) {
        return null;
      }

      List<Exprent> finalFirstExprents = root.firstExprents();
      Exprent finalNonNullCheck = initializer.nonNullCheck();

      return new SwitchOnReferenceCandidate(myRootSwitchStatement, mySwitchSelector, initializer.instance(), resortedCases,
                                            patternContainer,
                                            myTempVarAssignments, myUsedSwitch,
                                            usedTypeVarAssignments, root.doParentStatement(),
                                            initializer.nonNullCheck() != null, () -> {
        if (finalNonNullCheck != null) {
          finalFirstExprents.remove(finalNonNullCheck);
        }
      });
    }

    @NotNull
    private SwitchPatternHelper.JavacReferenceFinder.Initializer findInitializer(@NotNull Root root, Exprent typeVar) {
      Exprent instance = Objects.requireNonNull(mySwitchSelector.getInstance());

      Exprent nonNullCheck = null;
      AssignmentExprent initVar2 = null;

      for (Exprent exprent : root.firstExprents()) {
        if (exprent instanceof AssignmentExprent firstAssignment && instance.equals(firstAssignment.getLeft())) {
          instance = firstAssignment.getRight();
          if (firstAssignment.getLeft() instanceof VarExprent varExprent) {
            myTempVarAssignments.add(new TempVarAssignmentItem(varExprent, root.firstStatement()));
          }
        }
      }
      for (Exprent exprent : root.firstExprents()) {
        if (isNonNullCheck(exprent, instance)) {
          nonNullCheck = exprent;
        }
        if (exprent instanceof AssignmentExprent firstAssignment && typeVar.equals(firstAssignment.getLeft()) &&
            firstAssignment.getRight() instanceof ConstExprent constExprent &&
            constExprent.getValue() instanceof Integer value &&
            value == 0) {
          initVar2 = firstAssignment;

          if (firstAssignment.getLeft() instanceof VarExprent varExprent) {
            myTempVarAssignments.add(new TempVarAssignmentItem(varExprent, root.firstStatement()));
          }
        }
      }
      return new Initializer(instance, nonNullCheck, initVar2);
    }

    private record Initializer(Exprent instance, Exprent nonNullCheck, AssignmentExprent initVar2) {
    }

    /**
     * This method matches the structure from myRootSwitchStatement with common patterns to find root DoStatement.
     * DoStatement is used for pattern matching to repeat searching if a previous assumption is not correct.
     *
     * @return The `Root` object representing the root `DoStatement` and expression statements, or null if not found.
     */
    @Nullable
    private Root getRoot() {
      Statement first = myRootSwitchStatement.getFirst();
      if (first == null) {
        return null;
      }
      List<Exprent> firstExprents = first.getExprents();

      DoStatement doParentStatement = null;

      //possible structure (only for nested switch):
      //SequenceStatement
      //  ....
      //  Basic
      //  DoStatement
      //    DoStatement
      //      Switch
      if (myVarTracker != null && myRootSwitchStatement.getParent() instanceof DoStatement nestedDoStatement &&
          nestedDoStatement.getConditionExprent() == null &&
          nestedDoStatement.getParent() instanceof DoStatement upperDoStatement &&
          upperDoStatement.getConditionExprent() == null &&
          upperDoStatement.getParent() instanceof SequenceStatement sequenceStatement &&
          sequenceStatement.getStats().size() >= 2 && sequenceStatement.getStats().getLast() == upperDoStatement &&
          sequenceStatement.getStats().get(sequenceStatement.getStats().size() - 2) instanceof BasicBlockStatement basicBlockStatement) {
        firstExprents = basicBlockStatement.getExprents();
        first = basicBlockStatement;
        doParentStatement = nestedDoStatement;
      }
      //possible structure (only for nested switch):
      //SequenceStatement
      //  Switch
      //    default
      //    case
      //  DoStatement
      //    DoStatement
      //      Switch
      else if (myVarTracker != null && myRootSwitchStatement.getParent() instanceof DoStatement nestedDoStatement &&
               nestedDoStatement.getConditionExprent() == null &&
               nestedDoStatement.getParent() instanceof DoStatement upperDoStatement &&
               upperDoStatement.getConditionExprent() == null &&
               upperDoStatement.getParent() instanceof SequenceStatement sequenceStatement &&
               sequenceStatement.getStats().size() == 2 && sequenceStatement.getStats().get(1) == upperDoStatement &&
               sequenceStatement.getStats().get(0) instanceof SwitchStatement upperSwitchStatement &&
               upperSwitchStatement.getCaseStatements().size() == 2) {
        int indexDefault = upperSwitchStatement.getCaseEdges().get(0).contains(upperSwitchStatement.getDefaultEdge()) ? 0 :
                           (upperSwitchStatement.getCaseEdges().get(1).contains(upperSwitchStatement.getDefaultEdge()) ? 1 : -1);
        if (indexDefault == -1) {
          return null;
        }
        int other = indexDefault == 0 ? 1 : 0;
        Statement statement = upperSwitchStatement.getCaseStatements().get(other);
        if (statement.getStats().isEmpty()) {
          return null;
        }

        Statement last = statement.getStats().getLast();
        firstExprents = last.getExprents();
        first = last;
        doParentStatement = nestedDoStatement;
      }

      //possible structure (main case):
      //SequenceStatement
      //  ....
      //  BasicBlockStatement
      //    exprents with init values
      //  DoStatement
      //    SwitchStatement
      //  ....
      else if (firstExprents == null || firstExprents.stream().noneMatch(t -> t instanceof AssignmentExprent)) {
        Statement parent = myRootSwitchStatement.getParent();
        DoStatement doStatement = null;
        if (parent instanceof DoStatement) {
          doStatement = (DoStatement)parent;
        }
        if (doStatement == null && myRootSwitchStatement.getParent().getParent() instanceof DoStatement nextStatement) {
          doStatement = nextStatement;
        }
        if (doStatement == null ||
            doStatement.getLoopType() != DoStatement.LoopType.DO ||
            doStatement.getStats().size() != 1 ||
            !(doStatement.getParent() instanceof SequenceStatement upperStatement) ||
            upperStatement.getExprents() != null) {
          return null;
        }
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
        }
        //if previous is CatchStatement and initializers are inside CatchStatement
        else if (upperStatementStats.get(indexOfDo) == doStatement &&
                 upperStatementStats.get(indexOfDo - 1) instanceof CatchStatement catchStatement &&
                 catchStatement.getStats().size() >= 2 &&
                 catchStatement.getStats().get(1).getExprents() != null &&
                 !Objects.requireNonNull(catchStatement.getStats().get(1).getExprents()).isEmpty()) {
          firstExprents = catchStatement.getStats().get(1).getExprents();
          doParentStatement = doStatement;
          first = catchStatement.getStats().get(1);
        }
        else {
          return null;
        }
      }

      //check that this doParentStatement is not refered outside
      if (doParentStatement != null) {
        for (StatEdge edge : doParentStatement.getLabelEdges()) {
          if (edge.labeled && edge.explicit) {
            Statement source = edge.getSource();
            if (myRootSwitchStatement.containsStatement(source)) {
              continue;
            }
            Statement parent = myRootSwitchStatement.getParent();
            if (!(parent instanceof SequenceStatement sequenceStatement && sequenceStatement.getStats().size() == 2)) {
              return null;
            }
            int index = sequenceStatement.getStats().indexOf(myRootSwitchStatement);
            if (index != 0) {
              return null;
            }
            if (!(sequenceStatement.getStats().get(1) instanceof DoStatement firstDo &&
                  firstDo.getStats().size() == 1 && firstDo.getStats().get(0) instanceof DoStatement nestedDo)) {
              return null;
            }
            if (nestedDo.containsStatement(source)) {
              continue;
            }
            return null;
          }
        }
      }
      return new Root(first, firstExprents, doParentStatement);
    }

    /**
     * @return true if the bootstrap arguments of the switch selector can be processed (they represent Integer, String or Class),
     * false otherwise.
     */
    private boolean checkBootstrap() {
      List<PooledConstant> bootstrapArguments = mySwitchSelector.getBootstrapArguments();
      if (bootstrapArguments == null) {
        return true;
      }
      for (PooledConstant bootstrapArgument : bootstrapArguments) {
        if (!(bootstrapArgument instanceof PrimitiveConstant primitiveConstant)) {
          return true;
        }
        int type = primitiveConstant.type;
        if (!(type == CONSTANT_Integer || type == CONSTANT_String || type == CONSTANT_Class)) {
          return true;
        }
      }
      return false;
    }

    @SuppressWarnings({"SSBasedInspection", "SimplifyStreamApiCallChains"})
    @Nullable
    private static List<FullCase> resortForSwitchBootstrap(@NotNull SwitchStatement statement) {
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
          if (constExprent1.getIntValue() > constExprent2.getIntValue() &&
              constExprent1.getIntValue() != -1 &&
              constExprent2.getIntValue() != -1) {
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
      return sortedAll;
    }

    @NotNull
    private static VarExprent createDefaultPatternVal(@NotNull String className) {
      VarProcessor processor = DecompilerContext.getVarProcessor();
      VarExprent varExprent = new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER),
                                             new VarType(TYPE_OBJECT, 0, className),
                                             processor);
      varExprent.setDefinition(true);
      processor.setVarName(varExprent.getVarVersionPair(), VarExprent.getName(varExprent.getVarVersionPair()));
      return varExprent;
    }

    /**
     * @return A map of case statements from switch and their pattern variable candidates, or null if the collection fails.
     */
    @Nullable
    private Map<Statement, List<PatternVariableCandidate>> collectPatterns(@NotNull VarExprent instance,
                                                                           @Nullable DoStatement doStatement,
                                                                           @NotNull Set<AssignmentExprent> assignmentExprents) {

      Map<Statement, List<PatternVariableCandidate>> candidates = new HashMap<>();
      List<PooledConstant> bootstrapArguments = mySwitchSelector.getBootstrapArguments();
      Map<Integer, String> mapCaseClasses = getMapCaseClasses(bootstrapArguments);
      @NotNull List<List<@Nullable Exprent>> values = myRootSwitchStatement.getCaseValues();
      for (int caseIndex = 0; caseIndex < values.size(); caseIndex++) {
        List<Exprent> caseValues = values.get(caseIndex);
        for (int valueIndex = 0; valueIndex < caseValues.size(); valueIndex++) {
          Exprent caseValue = caseValues.get(valueIndex);
          if (!(caseValue instanceof ConstExprent constCaseValue)) {
            continue;
          }
          int expectedValue = constCaseValue.getIntValue();
          String className = mapCaseClasses.get(expectedValue);
          //if it doesn't have className, it is not a pattern (enum or literal), and can be processed during transformation
          if (className == null) {
            continue;
          }

          Statement caseStatement = myRootSwitchStatement.getCaseStatements().get(caseIndex);

          VarTracker currentVarTracker = myVarTracker;
          if (currentVarTracker == null) {
            RecordVarExprent recordVarExprent = new RecordVarExprent(instance);
            currentVarTracker = new VarTracker(recordVarExprent);
            currentVarTracker.put(instance, recordVarExprent, myRootSwitchStatement);
          }

          ConstExprent checkType = new ConstExprent(new VarType(className), null, null);
          currentVarTracker = currentVarTracker.copy();
          if (currentVarTracker == null) {
            continue;
          }

          PatternVariableCandidate candidate = null;

          //example:
          // some blocks
          // var2 = 2 // here var2 is second initializer
          // break
          //'break' breaks graph for inner statements, that's why it is needed to be excluded temporary
          if (caseStatement instanceof SequenceStatement sequenceStatement && !sequenceStatement.getStats().isEmpty()) {
            VBStyleCollection<Statement, Integer> sequenceStatementStats = sequenceStatement.getStats();
            Statement last = sequenceStatementStats.get(sequenceStatementStats.size() - 1);
            if (last instanceof BasicBlockStatement && last.getExprents() != null && last.getExprents().size() == 1) {
              Exprent exprent = last.getExprents().get(0);
              if (exprent instanceof AssignmentExprent assignmentExprent &&
                  assignmentExprent.getLeft() instanceof VarExprent left && myTypeVars.contains(left)) {
                //new sequence without break
                SequenceStatement newSequence = new SequenceStatement(sequenceStatementStats.subList(0, sequenceStatementStats.size() - 1));
                Map<Statement, Statement> previousParents = new HashMap<>();
                for (Statement currentStat : newSequence.getStats()) {
                  previousParents.put(currentStat, currentStat.getParent());
                  currentStat.setParent(newSequence);
                }
                newSequence.setParent(sequenceStatement.getParent());
                candidate = findNextPatternVarCandidate(newSequence, instance, checkType, currentVarTracker, newSequence);
                //'last' is not added here, because it must be processed in guards

                //return 'break' back
                for (Statement currentStat : newSequence.getStats()) {
                  Statement currentParent = previousParents.get(currentStat);
                  if (currentParent != null) {
                    currentStat.setParent(currentParent);
                  }
                }

                //candidate.nextStatement has broken edge here, it refers to 'last' (break)
                //it is expected if it has a guard
                if (candidate != null) {
                  candidate =
                    normalizeCandidateWithBrokenEdges(candidate, newSequence, sequenceStatement, last, doStatement, assignmentExprents,
                                                      assignmentExprent);
                  //it is impossible to normalize
                  if (candidate == null) {
                    return null;
                  }
                }
              }
            }
          }

          if (candidate == null) {
            candidate = findNextPatternVarCandidate(caseStatement, instance, checkType, currentVarTracker, caseStatement);
          }

          if (candidate != null) {
            List<PatternVariableCandidate> nestedSwitches =
              tryToFindNestedSwitch(candidate, caseStatement, assignmentExprents, currentVarTracker);
            if (nestedSwitches == null || nestedSwitches.isEmpty()) {
              //something broken
              return null;
            }
            candidates.put(caseStatement, nestedSwitches);
          }
          //there are no patterns for JAVA_LANG_OBJECT, add them as is
          else if (JAVA_LANG_OBJECT.equals(className)) {
            List<PatternVariableCandidate> value = new ArrayList<>();
            value.add(
              new PatternVariableCandidate(createDefaultPatternVal(className), caseStatement, new HashSet<>(), new ArrayList<>(), () -> {
              }));
            candidates.put(caseStatement, value);
          }
        }
        //collect from default case
        if (myRootSwitchStatement.getCaseEdges().get(caseIndex).contains(myRootSwitchStatement.getDefaultEdge())) {
          Statement defaultStatement = myRootSwitchStatement.getCaseStatements().get(caseIndex);
          if (defaultStatement instanceof BasicBlockStatement && defaultStatement.getExprents() != null &&
              defaultStatement.getExprents().size() == 1 &&
              defaultStatement.getExprents().get(0) instanceof AssignmentExprent assignmentExprent &&
              myTypeVars.contains(assignmentExprent.getLeft())) {
            assignmentExprents.add(assignmentExprent);
          }
        }
      }
      return candidates;
    }

    /**
     * Collects the guards and returns a PatternContainer object.
     *
     * @param candidates        a map containing the candidates for each case statement
     * @param typeVar           the type variable
     * @param doParentStatement the parent statement of the do statement
     * @param usedAssignments   a set of used assignments
     * @return a PatternContainer object containing the collected guards
     */
    @NotNull
    private SwitchPatternHelper.PatternContainer collectGuards(@NotNull Map<Statement, List<PatternVariableCandidate>> candidates,
                                                               @NotNull Exprent typeVar,
                                                               @Nullable Statement doParentStatement,
                                                               @NotNull Set<AssignmentExprent> usedAssignments) {
      PatternContainer container = new PatternContainer();
      @NotNull List<Statement> statements = myRootSwitchStatement.getCaseStatements();
      for (int i = 0; i < statements.size(); i++) {
        Statement previousValueStatement = statements.get(i);
        Statement caseStatement = statements.get(i);
        List<PatternVariableCandidate> variableCandidates = candidates.get(caseStatement);
        if (variableCandidates != null) {
          for (PatternVariableCandidate variableCandidate : variableCandidates) {
            container.addPattern(previousValueStatement, variableCandidate.getGuards(), variableCandidate.getNextStatement(),
                                 variableCandidate.getVarExprent());
          }
          //java 21, only one pattern can be with guard
          //todo check with unnamed patterns and variables
          if (variableCandidates.size() != 1) {
            continue;
          }
          caseStatement = variableCandidates.get(0).getNextStatement();
        }
        if (caseStatement.getStats().size() < 2) {
          continue;
        }
        if (doParentStatement == null) {
          continue;
        }
        List<@Nullable Exprent> exprents = myRootSwitchStatement.getCaseValues().get(i);
        OptionalInt maxCaseValue = exprents.stream().filter(t -> t instanceof ConstExprent)
          .mapToInt(t -> ((ConstExprent)t).getIntValue())
          .max();
        if (maxCaseValue.isEmpty()) {
          continue;
        }
        boolean guardIsNegated = false;

        if (!(caseStatement.getStats().get(0) instanceof IfStatement ifStatement &&
              ifStatement.getIfstat() != null &&
              ifStatement.getElsestat() == null &&
              ifStatement.iftype == IFTYPE_IF)) {
          continue;
        }
        AssignmentExprent assignmentExprent = null;

        if (ifStatement.getIfstat().getExprents() != null &&
            ifStatement.getIfstat().getExprents().size() == 1 &&
            ifStatement.getIfstat().getExprents().get(0) instanceof AssignmentExprent expectedAssignmentExprent &&
            expectedAssignmentExprent.getLeft() != typeVar && expectedAssignmentExprent.getRight() instanceof ConstExprent constExprent &&
            constExprent.getValue() instanceof Integer index && index > maxCaseValue.getAsInt()) {
          assignmentExprent = expectedAssignmentExprent;
          guardIsNegated = true;
        }

        if (!guardIsNegated) {
          Statement breakStatement = caseStatement.getStats().get(1);
          Statement expectedBreakStatement = caseStatement.getStats().get(caseStatement.getStats().size() - 1);
          List<StatEdge> successorEdges = expectedBreakStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
          if (successorEdges.size() != 1) {
            continue;
          }
          StatEdge breakEdge = successorEdges.get(0);
          if (breakEdge.getType() == EdgeType.REGULAR) {
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

        IfExprent ifExprent = ifStatement.getHeadexprent();
        Statement newCaseStatement = ifStatement.getIfstat();
        if (guardIsNegated) {
          ifExprent = ifExprent.negateIf();
          newCaseStatement = new SequenceStatement(caseStatement.getStats().subList(1, caseStatement.getStats().size()));
        }
        Exprent nameAssignment = Optional.ofNullable(ifStatement.getStats())
          .map(stats -> !stats.isEmpty() ? stats.get(0).getExprents() : null)
          .map(exprs -> exprs.size() == 1 ? exprs.get(0) : null)
          .orElse(null);

        usedAssignments.add(assignmentExprent);
        VarExprent nextValue = null;
        if (nameAssignment instanceof AssignmentExprent nameAssignmentExprent &&
            nameAssignmentExprent.getLeft() instanceof VarExprent varExprent) {
          myTempVarAssignments.add(new TempVarAssignmentItem(varExprent, ifStatement));
          nextValue = varExprent;
        }

        if (variableCandidates != null && variableCandidates.size() == 1) {
          nextValue = variableCandidates.get(0).getVarExprent();
        }
        container.replacePattern(previousValueStatement, ifExprent.getCondition(), newCaseStatement, nextValue);
      }
      return container;
    }

    /**
     * Normalizes the given PatternVariableCandidate by checking for broken edges and making the necessary adjustments.
     *
     * @param oldCandidate              The original PatternVariableCandidate to normalize.
     * @param newSequence               The new SequenceStatement being added.
     * @param previousSequenceStatement The previous SequenceStatement.
     * @param last                      The Statement that contains the break.
     * @param doStatement               The Root DoStatement
     * @param assignmentExprents        The set of AssignmentExprents to collect.
     * @param assignmentExprent         The AssignmentExprent to add (from last).
     * @return The normalized PatternVariableCandidate or null if normalization fails.
     */
    @Nullable
    private PatternVariableCandidate normalizeCandidateWithBrokenEdges(@NotNull PatternVariableCandidate oldCandidate,
                                                                       @NotNull SequenceStatement newSequence,
                                                                       @NotNull SequenceStatement previousSequenceStatement,
                                                                       @NotNull Statement last,
                                                                       @Nullable DoStatement doStatement,
                                                                       @NotNull Set<AssignmentExprent> assignmentExprents,
                                                                       @NotNull AssignmentExprent assignmentExprent) {

      PatternVariableCandidate candidate = oldCandidate;
      Statement nextStatement = candidate.getNextStatement();
      if (nextStatement == newSequence) {
        //if it has the same thing, let's reuse what we have; otherwise it will lead to a broken graph
        candidate = new PatternVariableCandidate(candidate.getVarExprent(), previousSequenceStatement,
                                                 candidate.getUsedIfStatement(), candidate.getTempAssignments(),
                                                 candidate.getCleaner()
        );
      }
      else if (nextStatement instanceof IfStatement ifStatement &&
               ifStatement.getSuccessorEdges(EdgeType.DIRECT_ALL).size() == 1 &&
               ifStatement.getSuccessorEdges(EdgeType.DIRECT_ALL).get(0).getDestination() == last) {
        ArrayList<Statement> statements = new ArrayList<>();
        statements.add(ifStatement);
        statements.add(last);
        candidate = new PatternVariableCandidate(candidate.getVarExprent(), new SequenceStatement(statements),
                                                 candidate.getUsedIfStatement(), candidate.getTempAssignments(),
                                                 candidate.getCleaner());
      }
      else if (nextStatement instanceof SequenceStatement nextSeqStat) {
        if (nextSeqStat.getStats().isEmpty()) {
          return null;
        }
        Statement lastNextStatement = nextSeqStat.getStats().get(nextSeqStat.getStats().size() - 1);
        List<StatEdge> edges = lastNextStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.size() != 1) {
          return null;
        }
        StatEdge edge = edges.get(0);
        if (edge.getDestination() != last) {
          //edge contains break, so it can be assumed that the firstStatement of assignmentExprent is processed
          if (!edgeCanBeWayOutOfRoot(doStatement, edge)) {
            //something broken
            return null;
          }
          assignmentExprents.add(assignmentExprent);
        }
        nextSeqStat.getStats().addWithKey(last, last.id);
      }
      else {
        List<StatEdge> edges = nextStatement.getSuccessorEdges(EdgeType.ALL);
        if (edges.size() == 1 && edges.get(0).getType() == EdgeType.BREAK) {
          StatEdge edge = edges.get(0);
          //edge contains break, so it can be assumed that the firstStatement of assignmentExprent is processed
          if (edgeCanBeWayOutOfRoot(doStatement, edge)) {
            assignmentExprents.add(assignmentExprent);
          }
        }
      }
      return candidate;
    }

    private boolean edgeCanBeWayOutOfRoot(@Nullable DoStatement doStatement, @NotNull StatEdge edge) {
      return edge.getDestination() instanceof DummyExitStatement ||
             (doStatement == null && edge.closure == myRootSwitchStatement) ||
             (doStatement != null && edge.closure == doStatement) ||
             (doStatement != null &&
              edge.closure instanceof DoStatement upperDoStatement &&
              upperDoStatement.containsStatement(doStatement)) ||
             outsideCatch(edge, doStatement);
    }

    private boolean outsideCatch(@NotNull StatEdge edge, @Nullable DoStatement doStatement) {
      Statement destination = edge.getDestination();
      Statement parent = destination.getParent();
      int indexOf = parent.getStats().indexOf(destination);
      if (indexOf < 1) {
        return false;
      }
      Statement previousStatement = parent.getStats().get(indexOf - 1);
      return previousStatement.containsStatement(myRootSwitchStatement) &&
             (doStatement == null || previousStatement.containsStatement(doStatement));
    }

    /**
     * Tries to find a nested switch statement according to patterns and process them recursively.
     *
     * @param previousCandidate      The previous pattern variable candidate
     * @param caseStatement          The case statement (from a previous switch statement)
     * @param usedAssignmentExprents The used assignment expressions
     * @param varTracker             The variable tracker
     * @return A list of pattern variable candidates or null, if it is impossible
     * List with only previousCandidate can be returned, if nested switch is not found
     */
    @Nullable
    private List<PatternVariableCandidate> tryToFindNestedSwitch(@NotNull PatternVariableCandidate previousCandidate,
                                                                 @NotNull Statement caseStatement,
                                                                 @NotNull Set<AssignmentExprent> usedAssignmentExprents,
                                                                 @NotNull VarTracker varTracker) {
      Set<AssignmentExprent> toAddAssignmentExprent = new HashSet<>();
      List<PatternVariableCandidate> defaultValue = new ArrayList<>();
      defaultValue.add(previousCandidate);

      Statement currentStatement = previousCandidate.getNextStatement();
      VBStyleCollection<Statement, Integer> currentStats = currentStatement.getStats();
      if (currentStats.isEmpty()) {
        return defaultValue;
      }
      SwitchStatement nestedSwitchStatement = null;
      if (!caseStatement.getLabelEdges().isEmpty()) {
        //example:
        //Sequence
        //  Switch
        //    case default
        //    case pattern
        //      initializers
        //  DoStatement
        //    DoStatement
        //      nested Switch
        Statement nextStatement = previousCandidate.getNextStatement();
        if (!(nextStatement instanceof SequenceStatement sequenceStatement && sequenceStatement.getStats().size() == 1 &&
              sequenceStatement.getStats().get(0) instanceof BasicBlockStatement && caseStatement.getLabelEdges().size() == 1)) {
          return defaultValue;
        }
        StatEdge edge = caseStatement.getLabelEdges().iterator().next();
        if (!(edge.getType() == EdgeType.BREAK && edge.getDestination() instanceof DoStatement upperDoStatement &&
              upperDoStatement.getConditionExprent() == null && upperDoStatement.getStats().size() == 1 &&
              upperDoStatement.getStats().get(0) instanceof DoStatement nestedDoStatement &&
              nestedDoStatement.getConditionExprent() == null && nestedDoStatement.getStats().size() == 1 &&
              nestedDoStatement.getStats().get(0) instanceof SwitchStatement switchStatement)) {
          return defaultValue;
        }
        if (!(caseStatement.getParent() instanceof SwitchStatement upperSwitchStatement &&
              upperSwitchStatement.getCaseStatements().size() == 2)) {
          return defaultValue;
        }
        int indexCurrentStat = upperSwitchStatement.getCaseStatements().indexOf(caseStatement);
        if (indexCurrentStat == -1) {
          return defaultValue;
        }
        int indexDefaultStatement = indexCurrentStat == 0 ? 1 : 0;
        if (!upperSwitchStatement.getCaseEdges()
          .get(indexDefaultStatement)
          .contains(upperSwitchStatement.getDefaultEdge())) {
          return defaultValue;
        }
        Statement defaultStatement = upperSwitchStatement.getCaseStatements().get(indexDefaultStatement);
        if (defaultStatement.getExprents() == null) {
          return defaultValue;
        }
        for (Exprent defaultExprent : defaultStatement.getExprents()) {
          if (defaultExprent instanceof AssignmentExprent assignmentExprent) {
            toAddAssignmentExprent.add(assignmentExprent);
          }
        }
        nestedSwitchStatement = switchStatement;
      }
      //current is switch
      else if (currentStats.get(0) instanceof SwitchStatement switchStatement) {
        nestedSwitchStatement = switchStatement;
      }
      else {
        if (currentStats.size() < 2) {
          return defaultValue;
        }
        if (!(currentStats.get(0) instanceof BasicBlockStatement)) {
          return defaultValue;
        }
        //Example:
        //Sequence
        //  BasicBlock
        //  Switch
        if (currentStats.get(1) instanceof SwitchStatement switchStatement) {
          nestedSwitchStatement = switchStatement;
        }
        //Example:
        //Sequence
        //  BasicBlock
        //  Do
        //    Switch
        if (currentStats.get(1) instanceof DoStatement doStatement && !doStatement.getStats().isEmpty() &&
            doStatement.getStats().get(0) instanceof SwitchStatement switchStatement) {
          nestedSwitchStatement = switchStatement;
        }
        //Example:
        //Sequence
        //  BasicBlock
        //  Do
        //    Sequence
        //      Switch
        if (currentStats.get(1) instanceof DoStatement doStatement && doStatement.getStats().size() == 1 &&
            doStatement.getStats().get(0) instanceof SequenceStatement sequenceStatement &&
            sequenceStatement.getStats().size() == 2 &&
            sequenceStatement.getStats().get(0) instanceof SwitchStatement newSwitchStatement) {
          nestedSwitchStatement = newSwitchStatement;
        }
        if (currentStats.get(1) instanceof DoStatement doStatement && doStatement.getStats().size() == 1 &&
            doStatement.getStats().get(0) instanceof DoStatement nestedDoStatement &&
            nestedDoStatement.getStats().size() == 1 &&
            nestedDoStatement.getStats().get(0) instanceof SwitchStatement newSwitchStatement) {
          nestedSwitchStatement = newSwitchStatement;
        }
      }

      if (nestedSwitchStatement == null) {
        return defaultValue;
      }
      if (!(nestedSwitchStatement.getHeadExprent() instanceof SwitchExprent switchExprent &&
            switchExprent.getValue() instanceof InvocationExprent invocationExprent)) {
        return defaultValue;
      }

      SwitchOnReferenceCandidate recognized =
        new JavacReferenceFinder(varTracker, myTypeVars, nestedSwitchStatement, invocationExprent).findCandidate();
      if (recognized == null) {
        return defaultValue;
      }
      VarExprent exprent = previousCandidate.getVarExprent();
      if (!(exprent instanceof RecordVarExprent currentRecordDeconstruction)) {
        return defaultValue;
      }
      RecordVarExprent component = currentRecordDeconstruction.getDirectComponent(recognized.myPreviousSelector.getInstance());
      if (component == null) {
        return defaultValue;
      }
      ArrayList<PatternVariableCandidate> candidates = new ArrayList<>();
      Map<Integer, String> classes = getMapCaseClasses(recognized.myPreviousSelector.getBootstrapArguments());
      for (FullCase fullCase : recognized.mySortedCasesFromRoot) {
        List<Exprent> fullCaseExprents = fullCase.exprents;
        Statement nestedStatement = fullCase.statement;
        List<StatEdge> edges = nestedStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.size() == 1) {
          StatEdge edge = edges.get(0);
          //not accurate, but it is hard to track all labels here
          if ((edge.getType() == EdgeType.CONTINUE || edge.getType() == EdgeType.BREAK) &&
              nestedStatement.getExprents() != null) {
            for (Exprent nestedExprent : nestedStatement.getExprents()) {
              if (nestedExprent instanceof AssignmentExprent assignmentExprent &&
                  assignmentExprent.getLeft() instanceof VarExprent left &&
                  myTypeVars.contains(left)) {
                toAddAssignmentExprent.add(assignmentExprent);
              }
            }
          }
        }
        List<PatternContainer.PatternStatement> nestedPatterns = recognized.myPatternContainer.getPatterns(nestedStatement);
        //process literals
        if (nestedPatterns == null || nestedPatterns.isEmpty()) {
          if (fullCaseExprents.size() == 2) {
            //delete null
            if (fullCaseExprents.get(0) instanceof ConstExprent constExprent && constExprent.getIntValue() == -1) {
              fullCaseExprents.remove(fullCaseExprents.get(0));
            }
          }
          if (fullCaseExprents.size() != 1) {
            return null;
          }
          if (fullCaseExprents.get(0) == null) {
            continue;
          }
          if (!(fullCaseExprents.get(0) instanceof ConstExprent constExprent)) {
            return null;
          }
          String newClassName = classes.get(constExprent.getIntValue());
          if (newClassName == null) {
            return null;
          }
          RecordVarExprent copy = currentRecordDeconstruction.copy();
          RecordVarExprent toReplace = copy.getDirectComponent(recognized.myPreviousSelector.getInstance());
          if (toReplace == null) {
            return null;
          }
          toReplace.setVarType(new VarType(newClassName));
          PatternVariableCandidate nestedPatternVariableCandidate =
            new PatternVariableCandidate(copy, fullCase.statement(), previousCandidate.getUsedIfStatement(),
                                         previousCandidate.getTempVarAssignments(), previousCandidate.getCleaner());
          candidates.add(nestedPatternVariableCandidate);
        }
        else {
          //process patterns
          for (PatternContainer.PatternStatement nestedPattern : nestedPatterns) {
            RecordVarExprent copy;
            if (nestedPattern.variable instanceof RecordVarExprent recordVarExprent) {
              copy = recordVarExprent;
            }
            else {
              if (nestedPattern.variable == null) {
                return null;
              }
              copy = currentRecordDeconstruction.copy();
              RecordVarExprent toReplace = copy.getDirectComponent(recognized.myPreviousSelector.getInstance());
              if (toReplace == null) {
                return null;
              }
              if (!toReplace.copyFrom(nestedPattern.variable)) {
                return null;
              }
            }
            PatternVariableCandidate nestedPatternVariableCandidate =
              new PatternVariableCandidate(copy, nestedPattern.caseStatement, previousCandidate.getUsedIfStatement(),
                                           previousCandidate.getTempVarAssignments(), previousCandidate.getCleaner());
            nestedPatternVariableCandidate.setGuards(nestedPattern.guard);
            candidates.add(nestedPatternVariableCandidate);
          }
        }
      }

      if (!candidates.isEmpty()) {
        candidates.get(0).getTempAssignments().addAll(recognized.myTempVarAssignments);
      }
      usedAssignmentExprents.addAll(recognized.myUsedTypeVarAssignments);
      usedAssignmentExprents.addAll(toAddAssignmentExprent);
      myUsedSwitch.add(nestedSwitchStatement);
      return candidates;
    }
  }

  @NotNull
  private static Map<Integer, String> getMapCaseClasses(List<PooledConstant> bootstrapArguments) {
    Map<Integer, String> mapCaseClasses = new HashMap<>();
    for (int i = 0; i < bootstrapArguments.size(); i++) {
      PooledConstant constant = bootstrapArguments.get(i);
      if (constant instanceof PrimitiveConstant primitiveConstant) {
        if (primitiveConstant.type == CONSTANT_Class) {
          mapCaseClasses.put(i, primitiveConstant.getString());
        }
      }
    }
    return mapCaseClasses;
  }

  private static boolean isNonNullCheck(@NotNull Exprent exprent, @NotNull Exprent switchVar) {
    if (!(exprent instanceof InvocationExprent invocationExprent)) {
      return false;
    }
    if (!invocationExprent.isStatic() || !"requireNonNull".equals(invocationExprent.getName()) ||
        !JAVA_UTIL_OBJECTS.equals(invocationExprent.getClassName()) ||
        invocationExprent.getParameters() == null ||
        invocationExprent.getParameters().size() != 1 ||
        !switchVar.equals(invocationExprent.getParameters().get(0))) {
      return false;
    }
    return true;
  }

  /**
   * Check if a variable is reinitialized a new value within a given statement and its child statements.
   * It checks if all nested statements are processed correctly for a given switch statement
   *
   * @param var       The variable expression to be reinitialized.
   * @param statement The statement in which to search for reinitialization of the variable.
   * @param exclude   A set of assignment expressions to be excluded from reinitialization check.
   * @return true if the variable is reinitialized in the given statement or its child statements, false otherwise.
   */
  private static boolean checkReinitVar(@NotNull Exprent var, @NotNull Statement statement, @NotNull Set<AssignmentExprent> exclude) {
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
        if (checkReinitVar(var, children, exclude)) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("SSBasedInspection")
  private static class SwitchOnReferenceCandidate implements SwitchOnCandidate {
    @NotNull
    private final SwitchStatement myRootSwitchStatement;
    @NotNull
    private final InvocationExprent myPreviousSelector;
    @NotNull
    private final Exprent myNewSwitchSelectorVariant;
    @NotNull
    private final List<TempVarAssignmentItem> myTempVarAssignments;
    @NotNull
    private final List<JavacReferenceFinder.FullCase> mySortedCasesFromRoot;

    @NotNull
    private final Set<SwitchStatement> myUsedSwitchStatements;
    @NotNull
    private final Set<AssignmentExprent> myUsedTypeVarAssignments;
    @Nullable
    private final Runnable myCleaner;
    @NotNull
    private final SwitchPatternHelper.PatternContainer myPatternContainer;
    @Nullable
    private final DoStatement myUppedDoStatement;
    private final boolean myMustCleanNonNull;

    private SwitchOnReferenceCandidate(@NotNull SwitchStatement rootSwitchStatement,
                                       @NotNull InvocationExprent previousSelector,
                                       @NotNull Exprent newSwitchSelectorVariant,
                                       @NotNull List<JavacReferenceFinder.FullCase> cases,
                                       @NotNull SwitchPatternHelper.PatternContainer patternContainer,
                                       @NotNull List<TempVarAssignmentItem> tempVarAssignments,
                                       @NotNull Set<SwitchStatement> usedSwitchStatements,
                                       @NotNull Set<AssignmentExprent> usedTypeVarAssignments,
                                       @Nullable DoStatement uppedDoStatement,
                                       boolean mustCleanNonNull,
                                       @Nullable Runnable cleaner) {
      this.myRootSwitchStatement = rootSwitchStatement;
      this.myPreviousSelector = previousSelector;
      this.myNewSwitchSelectorVariant = newSwitchSelectorVariant;
      this.myTempVarAssignments = tempVarAssignments;
      this.mySortedCasesFromRoot = cases;
      this.myUsedSwitchStatements = usedSwitchStatements;
      this.myCleaner = cleaner;
      this.myPatternContainer = patternContainer;
      this.myMustCleanNonNull = mustCleanNonNull;
      this.myUppedDoStatement = uppedDoStatement;
      this.myUsedTypeVarAssignments = usedTypeVarAssignments;
    }

    @Override
    public void simplify() {
      prepareSortedCases();

      if (myCleaner != null) {
        myCleaner.run();
      }
      resort(myRootSwitchStatement, mySortedCasesFromRoot);
      Exprent headExprent = myRootSwitchStatement.getHeadExprent();
      List<PooledConstant> bootstrapArguments = myPreviousSelector.getBootstrapArguments();
      Map<Integer, String> mapCaseClasses = getMapCaseClasses(bootstrapArguments);
      Map<Integer, Exprent> mapCaseValue = getMapCaseValue(bootstrapArguments);

      boolean hasPattern = remapCaseValues(mapCaseValue, mapCaseClasses);

      if (headExprent != null) {
        headExprent.replaceExprent(myPreviousSelector, myNewSwitchSelectorVariant);
      }
      if (hasPattern) {
        remapWithPatterns(myRootSwitchStatement, myPatternContainer, myUppedDoStatement, myTempVarAssignments);
        cleanDefault(myRootSwitchStatement);
      }
      Exprent oldSelector = myPreviousSelector.getInstance();
      if (oldSelector instanceof VarExprent oldSelectorVarExprent &&
          myNewSwitchSelectorVariant instanceof VarExprent newSwitchSelectorVarExprent) {
        changeEverywhere(oldSelectorVarExprent, newSwitchSelectorVarExprent, myRootSwitchStatement.getCaseStatements());
        changeDefaultToFullCase(myRootSwitchStatement, newSwitchSelectorVarExprent);
      }
    }

    private static void changeDefaultToFullCase(@NotNull SwitchStatement myRoot, @NotNull VarExprent newSwitch) {
      List<List<StatEdge>> edges = myRoot.getCaseEdges();
      for (int i = 0; i < edges.size(); i++) {
        List<StatEdge> statEdges = edges.get(i);
        if (statEdges.size() == 1 && statEdges.get(0) == myRoot.getDefaultEdge()) {
          Statement defaultStatement = myRoot.getCaseStatements().get(i);
          Statement statementWithFirstAssignment = getStatementWithFirstAssignment(defaultStatement);
          if (statementWithFirstAssignment!=null &&
              statementWithFirstAssignment.getExprents() != null &&
              !statementWithFirstAssignment.getExprents().isEmpty() &&
              statementWithFirstAssignment.getExprents().get(0) instanceof AssignmentExprent assignmentExprent &&
              assignmentExprent.getRight() != null &&
              assignmentExprent.getLeft() instanceof VarExprent newVarExprent &&
              assignmentExprent.getLeft().getExprType() != null &&
              assignmentExprent.getRight().equals(newSwitch) &&
              assignmentExprent.getLeft().getExprType().equals(newSwitch.getExprType())) {
            statementWithFirstAssignment.getExprents().remove(0);
            List<@Nullable Exprent> defaultValues = myRoot.getCaseValues().get(i);
            defaultValues.clear();
            defaultValues.add(newVarExprent);
            myRoot.setUseCustomDefault();
            break;
          }
        }
      }
    }

    @Nullable
    private static Statement getStatementWithFirstAssignment(@NotNull Statement statement) {
      if (statement.getExprents() != null && !statement.getExprents().isEmpty()) {
        if (statement.getExprents().get(0) instanceof AssignmentExprent) {
          return statement;
        }
        else {
          return null;
        }
      }
      if (!statement.getStats().isEmpty()) {
        return getStatementWithFirstAssignment(statement.getStats().get(0));
      }
      return null;
    }

    private static void changeEverywhere(@NotNull VarExprent oldVariant,
                                         @NotNull VarExprent newVariant,
                                         @NotNull List<Statement> statements) {
      for (Statement statement : statements) {
        if (statement.getExprents() != null) {
          for (Exprent exprent : statement.getExprents()) {
            for (Exprent nestedExprent : exprent.getAllExprents()) {
              if (nestedExprent.equals(oldVariant)) {
                exprent.replaceExprent(nestedExprent, newVariant);
              }
            }
          }
        }
        for (Statement nestedStat : statement.getStats()) {
          changeEverywhere(oldVariant, newVariant, nestedStat.getStats());
        }
      }
    }

    @Override
    public Set<SwitchStatement> usedSwitch() {
      return myUsedSwitchStatements;
    }

    @Override
    public List<TempVarAssignmentItem> prepareTempAssignments() {
      return this.myTempVarAssignments;
    }

    @NotNull
    private Map<Integer, Exprent> getMapCaseValue(List<PooledConstant> bootstrapArguments) {
      Map<Integer, Exprent> mapCaseValue = new HashMap<>();
      for (int i = 0; i < bootstrapArguments.size(); i++) {
        PooledConstant constant = bootstrapArguments.get(i);
        if (constant instanceof PrimitiveConstant primitiveConstant) {
          if (primitiveConstant.type == CONSTANT_Class) {
            continue;
          }
          if (myPreviousSelector.isDynamicCall("enumSwitch", 2)) {
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
      return mapCaseValue;
    }

    private void prepareSortedCases() {
      for (int i = 0; i < mySortedCasesFromRoot.size(); i++) {
        Statement currentStatement = mySortedCasesFromRoot.get(i).statement;
        List<StatEdge> edges = currentStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.size() == 1) {
          StatEdge onlyEdge = edges.get(0);
          if (onlyEdge.getType() != EdgeType.REGULAR && !onlyEdge.explicit) {
            onlyEdge.explicit = true;
            onlyEdge.labeled = false;
          }
          if (i == myRootSwitchStatement.getCaseStatements().size() - 1 &&
              onlyEdge.getType() == EdgeType.BREAK &&
              onlyEdge.explicit &&
              !onlyEdge.labeled) {
            onlyEdge.explicit = false;
          }
        }
      }

      if (myMustCleanNonNull) {
        JavacReferenceFinder.FullCase toDelete = null;
        for (JavacReferenceFinder.FullCase fullCase : mySortedCasesFromRoot) {
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
        mySortedCasesFromRoot.remove(toDelete);
      }
    }

    private boolean remapCaseValues(@NotNull Map<Integer, Exprent> mapCaseValue,
                                    @NotNull Map<Integer, String> mapCaseClasses) {
      @NotNull List<List<@Nullable Exprent>> values = myRootSwitchStatement.getCaseValues();
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
            List<PatternContainer.PatternStatement> guards =
              myPatternContainer.getPatterns(myRootSwitchStatement.getCaseStatements().get(caseIndex));
            if (guards != null && guards.size() == 1) {
              //if several, they will be remapped later
              newCaseValue = guards.get(0).variable();
            }
            if (newCaseValue == null) {
              newCaseValue = JavacReferenceFinder.createDefaultPatternVal(className);
            }
            hasPattern = true;
          }
          caseValues.set(valueIndex, newCaseValue);
        }
      }
      return hasPattern;
    }

    private static void remapWithPatterns(@NotNull SwitchStatement switchStatement,
                                          @Nullable SwitchPatternHelper.PatternContainer patternContainer,
                                          @Nullable DoStatement upperDoStatement,
                                          @NotNull List<TempVarAssignmentItem> tempVarAssignments) {
      if (patternContainer == null) {
        return;
      }
      extendCases(switchStatement, patternContainer);
      addGuards(switchStatement, patternContainer);
      if (upperDoStatement != null) {
        upperDoStatement.getParent().replaceStatement(upperDoStatement, switchStatement);
        normalizeCaseLabels(switchStatement, upperDoStatement);
      }
      normalizeLabels(switchStatement, tempVarAssignments);
      deleteNullCases(switchStatement);
    }

    private static void deleteNullCases(@NotNull SwitchStatement statement) {
      @NotNull List<List<@Nullable Exprent>> values = statement.getCaseValues();
      for (int i = 0; i < values.size(); i++) {
        List<Exprent> value = values.get(i);
        int indexOfNull = -1;
        Exprent nullExprent = null;
        for (int j = 0; j < value.size(); j++) {
          Exprent exprent = value.get(j);
          if (exprent instanceof ConstExprent constExprent && constExprent.isNull()) {
            nullExprent = constExprent;
            indexOfNull = j;
            break;
          }
        }
        if (nullExprent != null && value.stream().anyMatch(t -> t instanceof VarExprent)) {
          value.remove(nullExprent);
          statement.getCaseEdges().get(i).remove(indexOfNull);
        }
      }
    }

    private static void addGuards(@NotNull SwitchStatement switchStatement, @NotNull PatternContainer patternContainer) {
      for (int i = 0; i < switchStatement.getCaseStatements().size(); i++) {
        Statement currentCaseStatement = switchStatement.getCaseStatements().get(i);
        List<PatternContainer.PatternStatement> patterns = patternContainer.getPatterns(currentCaseStatement);
        if (patterns != null && patterns.size() == 1) {
          Statement newStatement = patterns.get(0).caseStatement();
          switchStatement.replaceStatement(switchStatement.getCaseStatements().get(i), newStatement);
          switchStatement.getCaseStatements().set(i, newStatement);
          switchStatement.getCaseEdges().get(i).forEach(edge -> {
                                                          edge.setDestination(patterns.get(0).caseStatement());
                                                        }
          );
          Exprent guard = patterns.get(0).guard();
          if (guard != null) {
            switchStatement.addGuard(switchStatement.getCaseStatements().get(i), guard);
          }
        }
      }
    }

    private static void extendCases(@NotNull SwitchStatement switchStatement, @NotNull PatternContainer patternContainer) {
      for (Map.Entry<Statement, List<PatternContainer.PatternStatement>> entry : patternContainer.patternsByStatement.entrySet()) {
        if (entry.getValue().size() == 1) {
          continue;
        }
        Statement currentStatement = entry.getKey();
        int indexOf = switchStatement.getCaseStatements().indexOf(currentStatement);
        if (indexOf < 0) {
          continue;
        }

        List<PatternContainer.PatternStatement> value = entry.getValue();
        for (int i = value.size() - 1; i >= 0; i--) {
          PatternContainer.PatternStatement patternStatement = value.get(i);
          int duplicatedIndex = switchStatement.duplicateCaseStatement(currentStatement);
          if (duplicatedIndex < 0) {
            continue;
          }
          switchStatement.replaceStatement(switchStatement.getCaseStatements().get(duplicatedIndex), patternStatement.caseStatement);
          switchStatement.getCaseEdges().get(duplicatedIndex).forEach(edge -> {
                                                                        edge.setDestination(patternStatement.caseStatement);
                                                                      }
          );
          VarExprent variable = patternStatement.variable();
          ArrayList<Exprent> newList = new ArrayList<>();
          newList.add(variable);
          switchStatement.getCaseValues().set(duplicatedIndex, newList);
          Exprent guard = patternStatement.guard;
          if (guard != null) {
            switchStatement.addGuard(patternStatement.caseStatement, guard);
          }
        }
        switchStatement.removeCaseStatement(currentStatement);
      }
    }

    private static void normalizeCaseLabels(@NotNull SwitchStatement switchStatement, @NotNull DoStatement upperDoStatement) {
      @NotNull List<Statement> statements = switchStatement.getCaseStatements();
      for (int i = 0; i < statements.size(); i++) {
        Statement caseStatement = statements.get(i);
        List<StatEdge> edges = caseStatement.getSuccessorEdges(EdgeType.DIRECT_ALL);
        if (edges.isEmpty() && !caseStatement.getStats().isEmpty()) {
          edges = caseStatement.getStats().get(caseStatement.getStats().size() - 1).getSuccessorEdges(EdgeType.DIRECT_ALL);
        }
        if (edges.size() == 1 && edges.get(0).getType() == EdgeType.BREAK &&
            (upperDoStatement.equals(edges.get(0).closure) || switchStatement.equals(edges.get(0).closure))) {
          edges.get(0).labeled = false;
        }
      }
    }

    private static void normalizeLabels(@NotNull SwitchStatement switchStatement,
                                        @NotNull List<TempVarAssignmentItem> tempVarAssignments) {
      Set<StatEdge> labelsToDelete = new HashSet<>();
      Set<VarExprent> tempVars = tempVarAssignments.stream().map(t -> t.varExprent()).collect(Collectors.toSet());
      for (StatEdge label : switchStatement.getLabelEdges()) {
        if (!label.explicit || !label.labeled) {
          continue;
        }
        Statement source = label.getSource();
        if (!(source instanceof BasicBlockStatement basicBlockStatement)) {
          continue;
        }
        boolean toDelete = true;
        if (basicBlockStatement.getExprents() == null) {
          continue;
        }
        for (Exprent exprent : basicBlockStatement.getExprents()) {
          if (!(exprent instanceof AssignmentExprent assignmentExprent)) {
            toDelete = false;
            break;
          }
          if (!tempVars.contains(assignmentExprent.getLeft())) {
            toDelete = false;
            break;
          }
        }
        if (toDelete) {
          labelsToDelete.add(label);
        }
      }
      switchStatement.getLabelEdges().removeAll(labelsToDelete);
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

    @SuppressWarnings({"SSBasedInspection", "SimplifyStreamApiCallChains"})
    private static void resort(@NotNull SwitchStatement statement, @NotNull List<JavacReferenceFinder.FullCase> cases) {
      statement.getCaseStatements().clear();
      statement.getCaseStatements().addAll(cases.stream().map(t -> t.statement).collect(Collectors.toList()));
      statement.getCaseEdges().clear();
      statement.getCaseEdges().addAll(cases.stream().map(t -> t.edges).collect(Collectors.toList()));
      statement.getCaseValues().clear();
      statement.getCaseValues().addAll(cases.stream().map(t -> t.exprents).collect(Collectors.toList()));
    }
  }

  private static class PatternContainer {
    private final Map<Statement, List<PatternStatement>> patternsByStatement = new HashMap<>();

    record PatternStatement(@NotNull Statement statement,
                            @Nullable Exprent guard,
                            @NotNull Statement caseStatement,
                            @Nullable VarExprent variable) {
    }

    void replacePattern(@NotNull Statement statement,
                        @Nullable Exprent guard,
                        @NotNull Statement caseStatement,
                        @Nullable VarExprent assignedName) {
      PatternStatement patternStatement = new PatternStatement(statement, guard, caseStatement, assignedName);
      ArrayList<PatternStatement> patterns = new ArrayList<>();
      patterns.add(patternStatement);
      patternsByStatement.put(statement, patterns);
    }

    void addPattern(@NotNull Statement statement,
                    @Nullable Exprent guard,
                    @NotNull Statement caseStatement,
                    @Nullable VarExprent assignedName) {
      patternsByStatement.compute(statement, (key, value) -> {
        if (value == null) {
          value = new ArrayList<>();
        }
        value.add(new PatternStatement(key, guard, caseStatement, assignedName));
        return value;
      });
    }

    @Nullable
    List<PatternStatement> getPatterns(@NotNull Statement statement) {
      return patternsByStatement.get(statement);
    }
  }
}
