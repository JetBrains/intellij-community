// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.ClassNameConstants;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;

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
   * Method searches and simplifies "switch-on-string" patterns in the statement graph.
   *
   * @param root statement to start traversal
   */
  public static void simplifySwitchesOnString(@NotNull RootStatement root) {
    List<SwitchOnStringCandidate> candidates = new ArrayList<>();
    collectSwitchesOnString(root, new ArrayList<>(
      Arrays.asList(new SwitchRecognizer.JavacRecognizer(), new SwitchRecognizer.EcjRecognizer())), candidates);
    if (candidates.isEmpty()) return;
    Map<VarExprent, Statement> tempVarAssignments = new HashMap<>();
    candidates.forEach(candidate -> candidate.simplify(tempVarAssignments));
    removeTempVariableDeclarations(tempVarAssignments);
  }

  private static void collectSwitchesOnString(@NotNull Statement statement,
                                              @NotNull List<SwitchRecognizer> recognizers,
                                              @NotNull List<SwitchOnStringCandidate> candidates) {
    if (statement instanceof SwitchStatement switchStatement) {
      SwitchExprent switchExprent = (SwitchExprent)switchStatement.getHeadExprent();
      Exprent switchSelector = Objects.requireNonNull(switchExprent).getValue();
      if (switchSelector instanceof InvocationExprent) {
        SwitchRecognizer usedRecognizer = null;
        for (SwitchRecognizer recognizer : recognizers) {
          SwitchOnStringCandidate switchCandidate = recognizer.recognize(switchStatement, (InvocationExprent)switchSelector);
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
      collectSwitchesOnString(child, recognizers, candidates);
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
      MethodWrapper wrapper = classNode.getWrapper().getMethodWrapper(CodeConstants.CLINIT_NAME, "()V");
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

    } else if (array.getArray().type == Exprent.EXPRENT_INVOCATION) { // Eclipse compiler
      InvocationExprent invocationExprent = (InvocationExprent) array.getArray();
      ClassesProcessor.ClassNode classNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(invocationExprent.getClassName());
      if (classNode == null) return mapping;
      MethodWrapper wrapper = classNode.getWrapper().getMethodWrapper(invocationExprent.getName(), "()[I");
      if (wrapper != null && wrapper.root != null) {
        wrapper.getOrBuildGraph().iterateExprents(exprent -> {
          if (exprent instanceof AssignmentExprent assignment) {
            Exprent left = assignment.getLeft();
            if (left.type == Exprent.EXPRENT_ARRAY) {
              Exprent indexExprent = ((ArrayExprent)left).getIndex();
              if (indexExprent.type == Exprent.EXPRENT_INVOCATION && ((InvocationExprent) indexExprent).getName().equals("ordinal")) {
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

  static void removeTempVariableDeclarations(@NotNull Map<VarExprent, Statement> tempVarAssignments) {
    if (tempVarAssignments.isEmpty()) return;
    for (Statement statement : new HashSet<>(tempVarAssignments.values())) {
      Statement parent = statement;
      while (parent != null) {
        boolean removed = false;
        List<Exprent> varExprents;
        if (parent.getFirst().type == StatementType.BASIC_BLOCK) {
          varExprents = parent.getFirst().getExprents();
        }
        else if (parent.type == StatementType.TRY_CATCH) {
          varExprents = parent.getVarDefinitions();
        }
        else {
          varExprents = Collections.emptyList();
        }
        for (int i = 0; i < varExprents.size(); i++) {
          Exprent exprent = varExprents.get(i);
          Exprent assignmentExprent = null;
          if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
            assignmentExprent = exprent;
            exprent = ((AssignmentExprent)exprent).getLeft();
          }
          if (exprent.type != Exprent.EXPRENT_VAR) continue;
          VarExprent varExprent = (VarExprent)exprent;
          if (varExprent.isDefinition() && tempVarAssignments.keySet().stream()
            .anyMatch(expr -> expr.getIndex() == varExprent.getIndex() && expr.getVersion() == varExprent.getVersion())) {
            varExprents.remove(assignmentExprent == null ? varExprent : assignmentExprent);
            removed = true;
            break;
          }
        }
        if (removed) break;
        parent = parent.getParent();
      }
    }
  }

  private abstract static class SwitchRecognizer {

    @Nullable
    abstract SwitchOnStringCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector);

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

    private static class JavacRecognizer extends SwitchRecognizer {
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
       * @see com.sun.tools.javac.comp.Lower#visitStringSwitch(com.sun.tools.javac.tree.JCTree.JCSwitch)
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
          if (ifStatementExprents.size() != 1) return null;
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

    private static class EcjRecognizer extends SwitchRecognizer {
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
          else if (switchSelectorQualifier.type == Exprent.EXPRENT_CONST) {
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

  private abstract static class SwitchOnStringCandidate {
    abstract void simplify(Map<VarExprent, Statement> tempVarAssignments);

    @Nullable
    private static AssignmentExprent addTempVarAssignment(@NotNull Exprent exprent,
                                                          @NotNull VarExprent varExprent,
                                                          @NotNull Statement statement,
                                                          @NotNull Map<VarExprent, Statement> tempVarAssignments) {
      if (exprent.type != Exprent.EXPRENT_ASSIGNMENT) return null;
      AssignmentExprent assignment = (AssignmentExprent)exprent;
      if (!varExprent.isDefinition() && varExprent.equals(assignment.getLeft())) {
        tempVarAssignments.put(varExprent, statement);
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
      void simplify(@NotNull Map<VarExprent, Statement> tempVarAssignments) {
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
            values.set(i, new ConstExprent(VarType.VARTYPE_STRING, labelValue, null));
          }
        }

        List<Exprent> firstSwitchExprents = firstStatementInFirstSwitch.getExprents();
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
      void simplify(@NotNull Map<VarExprent, Statement> tempVarAssignments) {
        Exprent switchSelector = switchStatement.getHeadExprent();
        if (switchSelector == null || switchSelector.type != Exprent.EXPRENT_SWITCH) return;
        tempVarAssignments.put(tmpVar, switchStatement);
        for (List<Exprent> values : switchStatement.getCaseValues()) {
          for (int i = 0; i < values.size(); i++) {
            ConstExprent constExprent = (ConstExprent)values.get(i);
            if (constExprent == null) continue;
            int caseLabelHash = constExprent.getIntValue();
            String labelValue = mappedCaseLabelValues.get(caseLabelHash);
            values.set(i, new ConstExprent(VarType.VARTYPE_STRING, labelValue, null));
            IfStatement ifStatement = mappedIfStatements.get(caseLabelHash);
            assert ifStatement.getStats().size() > 0;
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
