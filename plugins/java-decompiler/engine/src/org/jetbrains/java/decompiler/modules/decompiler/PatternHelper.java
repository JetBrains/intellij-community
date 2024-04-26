// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;

import static org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper.TempVarAssignmentItem;
import static org.jetbrains.java.decompiler.modules.decompiler.SwitchHelper.removeTempVariableDeclarations;
import static org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.EXIT_THROW;

public final class PatternHelper {

  public static final String MATCH_EXCEPTION = "java/lang/MatchException";

  /**
   * Method searches if-pattern like <code>if (var instanceof SomeType)</code> pattern,
   * and assignment expression pattern like <code>SomeType s = (SomeType)var;</code>.
   * If the pattern were found, then method replaces found assignment expression with pattern variable.
   *
   * @param statement root statement to start traversal
   * @param structClass owner class of <code>statement</code>
   */
  public static void replaceAssignmentsWithPatternVariables(@NotNull RootStatement statement, @NotNull StructClass structClass) {
    if (!structClass.hasPatternsInInstanceofSupport()) return;
    boolean recordPatternSupport = structClass.hasRecordPatternSupport();
    List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();

    List<Runnable> runnables = replaceAssignmentsWithPatternVariables(statement, new HashSet<>(), tempVarAssignments, recordPatternSupport);
    if (runnables.isEmpty() || !SwitchHelper.checkAssignmentsToDelete(statement, tempVarAssignments)) {
      return;
    }
    for (Runnable runnable : runnables) {
      runnable.run();
    }
    removeTempVariableDeclarations(tempVarAssignments);
  }

  /**
   * Replaces assignments with pattern variables in the provided statement.
   *
   * @return a list of Runnable objects containing the actions to replace assignments with pattern variables
   */
  private static List<Runnable> replaceAssignmentsWithPatternVariables(@NotNull Statement statement,
                                                                       @NotNull Set<IfStatement> usedIfStatements,
                                                                       @NotNull List<TempVarAssignmentItem> tempVarAssignments,
                                                                       boolean recordPatternSupport) {
    ArrayList<Runnable> actions = new ArrayList<>();
    if (statement instanceof IfStatement ifStatement && !usedIfStatements.contains(ifStatement)) {
      FunctionExprent instanceOfExprent = findInstanceofExprent(ifStatement);
      if (instanceOfExprent == null) return new ArrayList<>();

      List<Exprent> operands = instanceOfExprent.getLstOperands();
      if (operands.size() != 2 || operands.get(0).type != Exprent.EXPRENT_VAR || operands.get(1).type != Exprent.EXPRENT_CONST) return new ArrayList<>();
      VarExprent operand = (VarExprent)operands.get(0);
      ConstExprent checkType = (ConstExprent)operands.get(1);

      if (ifStatement.getIfstat() == null) {
        return new ArrayList<>();
      }
      Statement statementToChange = ifStatement.getIfstat();
      PatternVariableCandidate patternVarCandidate = findInitPatternVarCandidate(statementToChange, operand, checkType, recordPatternSupport, statementToChange);
      if (patternVarCandidate == null && ifStatement.getElsestat() != null) {
        statementToChange = ifStatement.getElsestat();
        patternVarCandidate = findInitPatternVarCandidate(statementToChange, operand, checkType, recordPatternSupport, statementToChange);
      }
      if (patternVarCandidate == null) return new ArrayList<>();
      tempVarAssignments.addAll(patternVarCandidate.getTempAssignments());
      usedIfStatements.add(ifStatement);
      usedIfStatements.addAll(patternVarCandidate.getUsedIfStatement());
      PatternVariableCandidate finalPatternVarCandidate = patternVarCandidate;
      Runnable action = ()-> {
        operands.remove(1);
        operands.add(finalPatternVarCandidate.getVarExprent());
        finalPatternVarCandidate.getCleaner().run();
      };
      actions.add(action);
    }
    for (Statement child : statement.getStats()) {
      actions.addAll(0, replaceAssignmentsWithPatternVariables(child, usedIfStatements, tempVarAssignments, recordPatternSupport));
    }
    return actions;
  }

  @Nullable
  private static FunctionExprent findInstanceofExprent(@NotNull IfStatement ifStat) {
    return ifStat.getHeadexprent().getAllExprents(true).stream()
      .filter(expr -> expr.type == Exprent.EXPRENT_FUNCTION).map(expr -> (FunctionExprent)expr)
      .filter(expr -> expr.getFuncType() == FunctionExprent.FUNCTION_INSTANCEOF)
      .findFirst().orElse(null);
  }

  static PatternVariableCandidate findInitPatternVarCandidate(@NotNull Statement ifElseStat,
                                                              @NotNull VarExprent operand,
                                                              @NotNull ConstExprent checkType,
                                                              boolean recordPatternSupport,
                                                              @NotNull Statement topLevelStatement) {
    if (ifElseStat instanceof BasicBlockStatement basicBlockStatement) {
      //check that cast correct and get the last assignment
      PatternVariableCandidate candidate = findSimpleCandidateFromIfStat(ifElseStat, operand, checkType, topLevelStatement);
      if (candidate == null) return null;
      //check what if it is record patterns
      if (recordPatternSupport && DecompilerContext.getOption(IFernflowerPreferences.CONVERT_RECORD_PATTERN)) {
        //collect everything from zero to check
        PatternVariableCandidate recordCandidate = findInitRecordPatternCandidate(basicBlockStatement, operand, candidate.getVarExprent());
        if (recordCandidate != null) {
          recordCandidate.getTempVarAssignments().addAll(candidate.getTempVarAssignments());
          return recordCandidate;
        }
      }
      return candidate;
    }
    else if (ifElseStat instanceof IfStatement || ifElseStat instanceof SequenceStatement) {
      return findInitPatternVarCandidate(ifElseStat.getFirst(), operand, checkType, recordPatternSupport, topLevelStatement);
    }
    return null;
  }

  @Nullable
  static PatternVariableCandidate findNextPatternVarCandidate(@NotNull Statement ifBranch,
                                                                      @NotNull VarExprent operand,
                                                                      @NotNull ConstExprent checkType,
                                                                      @NotNull VarTracker varTracker,
                                                                      @NotNull Statement topLevelStatement) {

    if (ifBranch instanceof BasicBlockStatement) {
      //check that cast correct and get the last assignment
      PatternVariableCandidate candidate = findSimpleCandidateFromIfStat(ifBranch, operand, checkType, topLevelStatement);
      //check what if it is record patterns
      VarTracker newVarTracker = varTracker.copy();
      if (newVarTracker == null) {
        return candidate;
      }
      RecordVarExprent previousRecord = newVarTracker.getRecord(operand);
      if (previousRecord == null) {
        return candidate;
      }
      previousRecord.setVarType(checkType.getConstType());
      if (!(ifBranch.getParent() instanceof IfStatement) &&  //prevent infinite recursion
          DecompilerContext.getOption(IFernflowerPreferences.CONVERT_RECORD_PATTERN)) {
        PatternVariableCandidate recordCandidate = findRecordPatternCandidate(ifBranch.getParent(), newVarTracker);
        if (recordCandidate != null) {
          varTracker.putAll(newVarTracker);
          return recordCandidate;
        }
      }
      return candidate;
    }
    if (ifBranch instanceof SequenceStatement || ifBranch instanceof IfStatement) {
      return findNextPatternVarCandidate(ifBranch.getFirst(), operand, checkType, varTracker, topLevelStatement);
    }
    return null;
  }

  @Nullable
  private static PatternVariableCandidate findSimpleCandidateFromIfStat(@NotNull Statement ifElseStat,
                                                                        @NotNull VarExprent operand,
                                                                        @NotNull ConstExprent checkType,
                                                                        @NotNull Statement topLevelStatement) {
    List<Exprent> ifElseExprents = ifElseStat.getExprents();
    if (ifElseExprents==null || ifElseExprents.isEmpty() || ifElseExprents.get(0).type != Exprent.EXPRENT_ASSIGNMENT) return null;

    AssignmentExprent assignmentExprent = (AssignmentExprent)ifElseExprents.get(0);
    if (assignmentExprent.getLeft().type != Exprent.EXPRENT_VAR) return null;
    VarExprent varExprent = (VarExprent)assignmentExprent.getLeft();
    if (assignmentExprent.getRight().type != Exprent.EXPRENT_FUNCTION) return null;
    FunctionExprent castExprent = (FunctionExprent)assignmentExprent.getRight();
    if (castExprent.getFuncType() != FunctionExprent.FUNCTION_CAST) return null;

    if (!varExprent.isDefinition()) {
      Exprent leftAssignmentPart = assignmentExprent.getLeft();
      Exprent rightAssignmentPart = assignmentExprent.getRight();
      if (leftAssignmentPart.type != Exprent.EXPRENT_VAR || rightAssignmentPart.type != Exprent.EXPRENT_FUNCTION ||
          ((FunctionExprent)rightAssignmentPart).getFuncType() != FunctionExprent.FUNCTION_CAST) {
        return null;
      }
      varExprent = ((VarExprent)leftAssignmentPart);
      List<Exprent> castOperands = ((FunctionExprent)rightAssignmentPart).getLstOperands();
      if (castOperands.size() != 2 || castOperands.get(1).type != Exprent.EXPRENT_CONST) return null;
      VarType castType = ((ConstExprent)castOperands.get(1)).getConstType();
      varExprent = (VarExprent)varExprent.copy();
      varExprent.setVarType(castType);
      varExprent.setDefinition(true);
    }

    List<Exprent> castExprents = castExprent.getAllExprents();
    if (castExprents.size() != 2 ||
        !operand.equals(castExprents.get(0)) ||
        !checkType.equals(castExprents.get(1))) {
      return null;
    }
    List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();
    if (!varExprent.isDefinition()) {
      tempVarAssignments.add(new TempVarAssignmentItem(varExprent, ifElseStat));
    }
    if (assignmentExprent.getLeft() instanceof VarExprent toDelete) {
      tempVarAssignments.add(new TempVarAssignmentItem(toDelete, ifElseStat));
    }
    return new PatternVariableCandidate(varExprent, topLevelStatement, new HashSet<>(), tempVarAssignments, () -> {
    });
  }

  private static PatternVariableCandidate findInitRecordPatternCandidate(@NotNull BasicBlockStatement blockStatement,
                                                                         @NotNull VarExprent varExprent,
                                                                         @NotNull VarExprent candidate) {
    Statement parent = blockStatement.getParent();
    if (!(parent instanceof SequenceStatement parentSequenceStatement)) {
      return null;
    }
    int indexOfBlock = parentSequenceStatement.getStats().indexOf(blockStatement);
    if (indexOfBlock != 0) {
      return null;
    }
    RecordVarExprent recordVarExprent = new RecordVarExprent(candidate);
    VarTracker varTracker = new VarTracker(recordVarExprent);
    varTracker.put(varExprent, recordVarExprent, null);
    return findRecordPatternCandidate(parentSequenceStatement, varTracker);
  }

  @Nullable
  private static PatternVariableCandidate findRecordPatternCandidate(@NotNull Statement parent,
                                                                     @NotNull VarTracker varTracker) {
    if (!(parent instanceof SequenceStatement)) {
      return null;
    }
    if (checkRegularEdgesForRecordPattern(parent)) return null;
    Set<IfStatement> ifStatements = new HashSet<>();

    VBStyleCollection<Statement, Integer> stats = parent.getStats();
    if (stats.size() < 3) {
      return null;
    }
    int i = 0;

    //It is possible to have false positive or false negative results here, and it is hard to exclude that.
    //Let's keep it simple at least
    while (true) {
      if (!(stats.size() > i + 1 && stats.get(i) instanceof BasicBlockStatement basicBlockStatement &&
            stats.get(i + 1) instanceof CatchStatement catchStatement)) {
        if (i != 0) {
          break;
        }
        return null;
      }
      //the first block must contain only assignments which can be gathered
      if (!processFullBlock(varTracker, basicBlockStatement)) {
        return null;
      }
      //a catch section with call for a record component
      if (!processCatchStatement(varTracker, catchStatement)) {
        return null;
      }
      i += 2;
    }
    int nextIndex = i;
    List<TempVarAssignmentItem> tempVarAssignmentItems = new ArrayList<>();
    if (!processAtLeastOneBlock(varTracker, stats.get(nextIndex))) {
      tempVarAssignmentItems.addAll(varTracker.getTempItems());
      //without reassigning Object
      SequenceStatement sequenceStatement =
        new SequenceStatement((parent.getStats().subList(nextIndex, parent.getStats().size())));
      return new PatternVariableCandidate(varTracker.root, sequenceStatement, new HashSet<>(), tempVarAssignmentItems, () -> {
        sequenceStatement.getStats().forEach(stat->stat.setParent(sequenceStatement));
        parent.getParent().replaceStatement(parent, sequenceStatement);
      });
    }
    //R(String s), there is the next if-statement, which defines the next class
    if (stats.get(nextIndex) instanceof IfStatement ifStatement && stats.size() - 1 == nextIndex) {
      PatternVariableCandidate nestedCandidate = findRecursivelyInstanceOfIfStatement(stats.get(nextIndex), varTracker, tempVarAssignmentItems);
      if (nestedCandidate != null) {
        ifStatements.add(ifStatement);
        ifStatements.addAll(nestedCandidate.getUsedIfStatement());
        tempVarAssignmentItems.addAll(varTracker.getTempItems());
        tempVarAssignmentItems.addAll(nestedCandidate.getTempVarAssignments());
        return new PatternVariableCandidate(varTracker.root, nestedCandidate.getNextStatement(), ifStatements, tempVarAssignmentItems, () -> {
          nestedCandidate.getCleaner().run();
          parent.getParent().replaceStatement(parent, ifStatement.getIfstat());
        });
      }
    }
    //R(Object o), without defining the next class.
    tempVarAssignmentItems.addAll(varTracker.getTempItems());
    SequenceStatement sequenceStatement =
      new SequenceStatement((parent.getStats().subList(nextIndex, parent.getStats().size())));
    return new PatternVariableCandidate(varTracker.root, sequenceStatement, new HashSet<>(), tempVarAssignmentItems, () -> {
      sequenceStatement.getStats().forEach(stat->stat.setParent(sequenceStatement));
      parent.getParent().replaceStatement(parent, sequenceStatement);
    });
  }

  private static PatternVariableCandidate findRecursivelyInstanceOfIfStatement(@NotNull Statement statement,
                                                                               @NotNull VarTracker tracker,
                                                                               @NotNull List<TempVarAssignmentItem> tempVarAssignmentItems) {
    if (!(statement instanceof IfStatement ifStatement)) {
      return null;
    }
    if (ifStatement.isNegated() || ifStatement.getHeadexprent().getAllExprents().size() != 1) {
      return null;
    }
    FunctionExprent instanceOfExprent = findInstanceofExprent(ifStatement);
    if (instanceOfExprent == null) {
      return null;
    }
    List<Exprent> operands = instanceOfExprent.getLstOperands();
    if (operands.size() != 2 || operands.get(0).type != Exprent.EXPRENT_VAR || operands.get(1).type != Exprent.EXPRENT_CONST) {
      return null;
    }
    VarExprent operand = (VarExprent)operands.get(0);
    ConstExprent checkType = (ConstExprent)operands.get(1);
    RecordVarExprent recordVarExprent = tracker.getRecord(operand);
    if (recordVarExprent == null) {
      return null;
    }

    PatternVariableCandidate candidate = findNextPatternVarCandidate(ifStatement.getIfstat(), operand, checkType, tracker, ifStatement.getIfstat());
    if (candidate == null) {
      return null;
    }
    if (!recordVarExprent.copyFrom(candidate.getVarExprent())) {
      return null;
    }
    tempVarAssignmentItems.addAll(candidate.getTempVarAssignments());
    tracker.put(candidate.getVarExprent(), recordVarExprent, null);
    return candidate;
  }

  /**
   * Processes a catch statement to check if it matches a specific pattern that involves
   * record assignments. If the pattern matches, the method updates the given VarTracker
   * with the record assignment information.
   * Expected pattern:
   * try{
   *   Object o = record.component();
   * } catch(Throwable e){
   *   throw new MatchException(_, _);
   * }
   *
   * @param tracker   the VarTracker used to track record assignments
   * @param statement the CatchStatement to process
   * @return true if the catch statement matches the pattern and was successfully processed,
   *         false otherwise
   */
  private static boolean processCatchStatement(@NotNull VarTracker tracker,
                                               @NotNull CatchStatement statement) {
    Statement tryBody = statement.getFirst();
    if (tryBody == null || tryBody.getStats() == null || !tryBody.getStats().isEmpty() ||
        tryBody.getExprents() == null || tryBody.getExprents().size() != 1) {
      return false;
    }
    Exprent body = tryBody.getExprents().get(0);
    InvocationExprent invocationExprent = null;
    boolean hasAssignment = false;
    if (body instanceof AssignmentExprent assignmentExprent && assignmentExprent.getRight() instanceof InvocationExprent newInvocation) {
      invocationExprent = newInvocation;
      hasAssignment = true;
    }
    else if (body instanceof InvocationExprent newInvocation) {
      invocationExprent = newInvocation;
    }
    if (invocationExprent == null) {
      return false;
    }
    Exprent qualifier = invocationExprent.getInstance();
    if (qualifier == null ||
        invocationExprent.isStatic() ||
        !(invocationExprent.getParameters() != null && invocationExprent.getParameters().isEmpty())) {
      return false;
    }
    RecordVarExprent recordVarExprent = tracker.getRecord(qualifier);
    if (recordVarExprent == null) {
      return false;
    }
    if (statement.getStats().size() != 2) {
      return false;
    }
    Statement catchSection = statement.getStats().get(1);
    if (!(catchSection.getStats().isEmpty() && catchSection.getExprents() != null)) {
      return false;
    }
    if (!((catchSection.getExprents().size() == 1) ||
          (catchSection.getExprents().size() == 2 && catchSection.getExprents().get(1) instanceof ExitExprent))) {
      return false;
    }
    Exprent throwExpected = catchSection.getExprents().get(0);
    if (!((throwExpected instanceof ExitExprent exitExprent && exitExprent.getExitType() == EXIT_THROW &&
          exitExprent.getValue() instanceof NewExprent newExprent && newExprent.getNewType() != null &&
          MATCH_EXCEPTION.equals(newExprent.getNewType().getValue())) ||
        (catchSection.getExprents().get(1) instanceof ExitExprent))) {
      return false;
    }

    if (!hasAssignment) {
      VarType exprType = invocationExprent.getExprType();
      VarProcessor processor = DecompilerContext.getVarProcessor();
      RecordVarExprent nextComponent = new RecordVarExprent(new VarExprent(DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), exprType,processor ));
      recordVarExprent.addComponent(nextComponent);
      return true;
    }
    if (!(((AssignmentExprent)body).getLeft() instanceof VarExprent assignTo)) {
      return false;
    }
    VarExprent assignToDefinition = (VarExprent)assignTo.copy();
    assignToDefinition.setDefinition(true);
    RecordVarExprent nextComponent = new RecordVarExprent(assignToDefinition);
    recordVarExprent.addComponent(nextComponent);
    tracker.put(assignTo, nextComponent, tryBody);

    return true;
  }

  /**
   * Process all exprents in a statement, collecting record assignments in the given statement into the tracker.
   *
   * @param tracker    the VarTracker to collect record assignments
   * @param statement  the Statement to process
   * @return true if the process completed successfully, false otherwise
   */
  private static boolean processFullBlock(@NotNull VarTracker tracker,
                                          @NotNull Statement statement) {
    if (statement instanceof BasicBlockStatement && statement.getExprents() != null) {
      for (Exprent statementExprent : statement.getExprents()) {
        if (!collectRecordAssignment(tracker, statementExprent, statement)) return false;
      }
    }
    return true;
  }

  /**
   * Process at least first exprent in a statement.
   * Collects record assignments in the given statement into the tracker.
   *
   * @param tracker   the VarTracker to collect record assignments
   * @param statement the Statement to process
   * @return true if at least first exprent is processed, false otherwise.
   */
  static boolean processAtLeastOneBlock(@NotNull VarTracker tracker, @NotNull Statement statement) {
    if (statement instanceof BasicBlockStatement  && statement.getExprents() != null) {
      boolean found = false;
      for (Exprent statementExprent : statement.getExprents()) {
        if (collectRecordAssignment(tracker, statementExprent, statement)) {
          found = true;
          continue;
        }
        return found;
      }
    }
    if (statement instanceof SequenceStatement || statement instanceof IfStatement) {
      return processAtLeastOneBlock(tracker, statement.getFirst());
    }
    return true;
  }

  /**
   * Collects record assignment in the given statement into tracker.

   * @return True if a record assignment was found and processed, false otherwise.
   */
  private static boolean collectRecordAssignment(@NotNull VarTracker tracker,
                                                 @Nullable Exprent exprent,
                                                 @NotNull Statement statement) {
    if (!(exprent instanceof AssignmentExprent assignmentExprent)) {
      return false;
    }
    Exprent exprentLeft = assignmentExprent.getLeft();
    if (!(exprentLeft instanceof VarExprent leftVarExprent)) {
      return false;
    }
    Exprent right = assignmentExprent.getRight();
    if (right instanceof VarExprent varExprent) {
      RecordVarExprent reassignedRecord = tracker.getRecord(varExprent);
      if (reassignedRecord == null) {
        return false;
      }
      reassignedRecord.copyFrom(leftVarExprent);
      tracker.put(leftVarExprent, reassignedRecord, statement);
    }
    else if (right instanceof FunctionExprent functionExprent &&
             functionExprent.getFuncType() == FunctionExprent.FUNCTION_CAST &&
             functionExprent.getLstOperands().size() == 2 &&
             functionExprent.getLstOperands().get(1) instanceof ConstExprent constExprent) {
      Exprent varForCast = functionExprent.getLstOperands().get(0);
      RecordVarExprent recordToCast = tracker.getRecord(varForCast);
      if (recordToCast == null) {
        return false;
      }
      if (!recordToCast.getVarType().equals(constExprent.getExprType())) {
        return false;
      }
      recordToCast.copyFrom(leftVarExprent);
      tracker.put(leftVarExprent, recordToCast, statement);
    }
    else {
      return false;
    }
    return true;
  }

  private static boolean checkRegularEdgesForRecordPattern(@NotNull Statement parent) {
    for (int i = 0; i < parent.getStats().size(); i++) {
      Statement statement = parent.getStats().get(i);
      if (i != parent.getStats().size() - 1) {
        List<StatEdge> edges = statement.getSuccessorEdges(StatEdge.EdgeType.DIRECT_ALL);
        if (edges.size() != 1) {
          return true;
        }
        StatEdge edge = edges.get(0);
        if (edge.getType() != StatEdge.EdgeType.REGULAR || edge.getDestination() != parent.getStats().get(i + 1)) {
          return true;
        }
      }
    }
    return false;
  }

  static class PatternVariableCandidate {
    private final @NotNull VarExprent varExprent;
    private final @NotNull List<TempVarAssignmentItem> tempVarAssignments;
    private final @NotNull Runnable cleaner;
    private final @NotNull Set<IfStatement> usedIfStatement = new HashSet<>();
    private final @NotNull Statement nextStatement;
    @Nullable private Exprent guards;

    PatternVariableCandidate(@NotNull VarExprent varExprent,
                             @NotNull Statement nextStatement,
                             @NotNull Set<IfStatement> usedIfStatement,
                             @NotNull List<TempVarAssignmentItem> tempVarAssignments,
                             @NotNull Runnable cleaner) {
      this.varExprent = varExprent;
      this.tempVarAssignments = tempVarAssignments;
      this.cleaner = cleaner;
      this.getUsedIfStatement().addAll(usedIfStatement);
      this.nextStatement = nextStatement;
    }

    List<TempVarAssignmentItem> getTempAssignments() {
      return getTempVarAssignments();
    }

    @NotNull VarExprent getVarExprent() {
      return varExprent;
    }

    @NotNull List<TempVarAssignmentItem> getTempVarAssignments() {
      return tempVarAssignments;
    }

    @NotNull Runnable getCleaner() {
      return cleaner;
    }

    @NotNull Set<IfStatement> getUsedIfStatement() {
      return usedIfStatement;
    }

    @NotNull Statement getNextStatement() {
      return nextStatement;
    }

    @Nullable Exprent getGuards() {
      return guards;
    }

    void setGuards(@Nullable Exprent guards) {
      this.guards = guards;
    }
  }

  static class VarTracker {
    @NotNull
    private RecordVarExprent root;
    @NotNull
    private final Map<VarExprent, RecordVarExprent> varRecordTracker = new HashMap<>();
    @NotNull
    private final List<TempVarAssignmentItem> varTempAssignmentTracker = new ArrayList<>();

    VarTracker(@NotNull RecordVarExprent root) { this.root = root; }

    void put(@NotNull VarExprent varExprent,
             @NotNull RecordVarExprent recordVarExprent,
             @Nullable Statement statement) {
      varRecordTracker.put(varExprent, recordVarExprent);
      if (statement != null) {
        varTempAssignmentTracker.add(new TempVarAssignmentItem(varExprent, statement));
      }
    }

    @Nullable
    RecordVarExprent getRecord(@NotNull Exprent exp) {
      return varRecordTracker.get(exp);
    }

    @NotNull
    List<TempVarAssignmentItem> getTempItems() {
      return varTempAssignmentTracker;
    }

    @Nullable
    VarTracker copy() {
      RecordVarExprent copy = root.copy();
      Map<RecordVarExprent, RecordVarExprent> mapToNew = new HashMap<>();
      Queue<Map.Entry<RecordVarExprent, RecordVarExprent>> queue = new ArrayDeque<>();
      queue.add(Map.entry(root, copy));
      while (!queue.isEmpty()) {
        Map.Entry<RecordVarExprent, RecordVarExprent> nextPair = queue.poll();
        mapToNew.put(nextPair.getKey(), nextPair.getValue());
        List<RecordVarExprent> oldComponents = nextPair.getKey().getComponents();
        List<RecordVarExprent> newComponents = nextPair.getValue().getComponents();
        if (oldComponents.size() != newComponents.size()) {
          return null;
        }
        for (int i = 0; i < oldComponents.size(); i++) {
          queue.add(Map.entry(oldComponents.get(i), newComponents.get(i)));
        }
      }
      Map<VarExprent, RecordVarExprent> newVarRecordTracker = new HashMap<>();
      for (Map.Entry<VarExprent, RecordVarExprent> entry : varRecordTracker.entrySet()) {
        RecordVarExprent newExprent = mapToNew.get(entry.getValue());
        newVarRecordTracker.put(entry.getKey(), newExprent);
      }
      VarTracker newTracker = new VarTracker(copy);
      newTracker.varRecordTracker.putAll(newVarRecordTracker);
      newTracker.varTempAssignmentTracker.addAll(varTempAssignmentTracker);
      return newTracker;
    }

    private void putAll(@NotNull VarTracker tracker) {
      root = tracker.root;
      varRecordTracker.putAll(tracker.varRecordTracker);
      varTempAssignmentTracker.addAll(tracker.varTempAssignmentTracker);
    }
  }
}
