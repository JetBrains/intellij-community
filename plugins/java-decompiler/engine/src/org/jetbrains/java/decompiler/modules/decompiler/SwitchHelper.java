// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.ClassNameConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.struct.StructClass;

import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.java.decompiler.code.CodeConstants.CLINIT_NAME;
import static org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import static org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent.FUNCTION_CAST;
import static org.jetbrains.java.decompiler.struct.gen.VarType.VARTYPE_STRING;

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
                    new SwitchPatternHelper.JavacReferenceRecognizer()
      )), candidates, new HashSet<>());
    if (candidates.isEmpty()) return;
    List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();
    candidates.forEach(candidate -> tempVarAssignments.addAll(candidate.prepareTempAssignments()));
    if (!checkAssignmentsToDelete(root, tempVarAssignments)) {
      return;
    }
    candidates.forEach(candidate -> candidate.simplify());
    removeTempVariableDeclarations(tempVarAssignments);
  }

  /**
   * @return true, if all similar nested assignments have been processed, false otherwise,
   * it helps to prevent some false positive cases
   */
  static boolean checkAssignmentsToDelete(@NotNull Statement parent,
                                          @NotNull List<TempVarAssignmentItem> items) {
    Map<VarExprent, List<VarExprent>> collected = items.stream().map(t -> t.varExprent()).collect(Collectors.groupingBy(t -> t));
    return checkRecursivelyAssignmentsToDelete(parent, collected);
  }

  private static boolean checkRecursivelyAssignmentsToDelete(@NotNull Statement statement,
                                                             @NotNull Map<VarExprent, List<VarExprent>> collected) {
    List<Exprent> exprents = statement.getExprents();
    if (exprents != null) {
      for (Exprent exprent : exprents) {
        if (!(exprent instanceof AssignmentExprent assignmentExprent)) {
          continue;
        }
        Exprent right = assignmentExprent.getRight();
        Exprent left = assignmentExprent.getLeft();
        boolean containsLeft = containVar(collected, left);
        if (right instanceof VarExprent varExprent) {
          boolean containsRight = containVar(collected, varExprent);
          if (containsLeft || !containsRight) {
            continue;
          }
          return false;
        }
        if (right instanceof FunctionExprent functionExprent &&
            functionExprent.getFuncType() == FUNCTION_CAST &&
            functionExprent.getLstOperands().size() == 2 &&
            functionExprent.getLstOperands().get(1) instanceof ConstExprent) {
          Exprent operand = functionExprent.getLstOperands().get(0);
          if (operand instanceof VarExprent varExprent) {
            boolean containsRight = containVar(collected, varExprent);
            if (containsLeft || !containsRight) {
              continue;
            }
            return false;
          }
        }
      }
    }
    for (Statement child : statement.getStats()) {
      if (!checkRecursivelyAssignmentsToDelete(child, collected)) {
        return false;
      }
    }
    return true;
  }

  private static void collectSwitchesOn(@NotNull Statement statement,
                                        @NotNull List<SwitchRecognizer> recognizers,
                                        @NotNull List<SwitchOnCandidate> candidates,
                                        @NotNull Set<SwitchStatement> usedSwitchStatement) {
    if (statement instanceof SwitchStatement switchStatement && !usedSwitchStatement.contains(switchStatement)) {
      SwitchExprent switchExprent = (SwitchExprent)switchStatement.getHeadExprent();
      Exprent switchSelector = Objects.requireNonNull(switchExprent).getValue();
      if (switchSelector instanceof InvocationExprent) {
        for (SwitchRecognizer recognizer : recognizers) {
          SwitchOnCandidate switchCandidate = recognizer.recognize(switchStatement, (InvocationExprent)switchSelector);
          if (switchCandidate == null) continue;
          candidates.add(0, switchCandidate);
          usedSwitchStatement.addAll(switchCandidate.usedSwitch());
          break;
        }
      }
    }
    for (Statement child : statement.getStats()) {
      collectSwitchesOn(child, recognizers, candidates, usedSwitchStatement);
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

  static void removeTempVariableDeclarations(@NotNull List<TempVarAssignmentItem> tempVarAssignments) {
    if (tempVarAssignments.isEmpty()) return;
    Set<Statement> visited = new HashSet<>();
    Set<Statement> statements = tempVarAssignments.stream().map(a -> a.statement()).collect(Collectors.toSet());
    Map<VarExprent, List<VarExprent>> vars = tempVarAssignments.stream().map(a -> a.varExprent()).collect(Collectors.groupingBy(t -> t));
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
            if (containVar(vars, varExprent) || (varExprent.isDefinition() && vars.containsKey(varExprent))) {
              toDelete.add(assignmentExprent == null ? varExprent : assignmentExprent);
            }
          }
          varExprents.removeAll(toDelete);
        }
        parent = parent.getParent();
      }
    }
  }

  private static boolean containVar(@NotNull Map<VarExprent, List<VarExprent>> vars, @Nullable Exprent exprent) {
    if (exprent == null) {
      return false;
    }
    List<VarExprent> exprents = vars.get(exprent);
    if (exprents == null) {
      return false;
    }
    for (VarExprent varExprent : exprents) {
      if (exprent == varExprent) {
        //exactly the same reference
        return true;
      }
    }
    return false;
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

  interface SwitchRecognizer {
    @Nullable
    SwitchOnCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector);
  }

  private abstract static class StringSwitchRecognizer implements SwitchRecognizer {

    @Override
    @Nullable
    public abstract SwitchOnStringCandidate recognize(@NotNull SwitchStatement statement, @NotNull InvocationExprent switchSelector);

    @NotNull
    Set<Object> findRealCaseValuesHashCodes(@NotNull SwitchStatement switchStatement) {
      // noinspection SSBasedInspection
      return switchStatement.getCaseValues().stream()
        // we take only buckets that don't contain null value.
        // Null value represents default branch and no temp variable is assigned there.
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
       * @return recognized a switch candidate
       * see Lower.visitStringSwitch(JCTree.JCSwitch) in javac
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

        Exprent secondSwitchSelector = secondSwitch.getHeadExprent();
        if (secondSwitchSelector == null || secondSwitchSelector.type != Exprent.EXPRENT_SWITCH) return null;
        Statement firstStatementInFirstSwitch = firstSwitch.getFirst();
        if (firstStatementInFirstSwitch.type != StatementType.BASIC_BLOCK) return null;
        Statement firstStatementInSecondSwitch = secondSwitch.getFirst();
        if (firstStatementInSecondSwitch.type != StatementType.BASIC_BLOCK) return null;
        List<Exprent> firstSwitchExprents = firstStatementInFirstSwitch.getExprents();
        if (firstSwitchExprents == null || firstStatementInSecondSwitch.getExprents() == null) return null;

        return new SwitchOnStringCandidate.JavacSwitchCandidate(firstSwitch, firstSwitchSelectorQualifier, tmpVarAssignTo, secondSwitch,
                                                                mappedCaseLabelValues,
                                                                firstSwitchExprents,
                                                                secondSwitchSelector,
                                                                firstStatementInSecondSwitch);
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
            // Ecj inlines compile constants, so we try to find a temp variable assigned to the same const value.
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

  interface SwitchOnCandidate {
    void simplify();

    Set<SwitchStatement> usedSwitch();

    List<TempVarAssignmentItem> prepareTempAssignments();
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
      @NotNull private final SwitchStatement secondSwitch;
      @NotNull private final VarExprent firstSwitchSelector;
      @NotNull private final Exprent secondSwitchSelector;
      @NotNull private final VarExprent tmpVarInFirstSwitch;
      @NotNull private final Map<Integer, @NotNull String> mappedCaseLabelValues;
      @NotNull private final List<Exprent> firstSwitchExprents;
      @NotNull private final Statement firstStatementInSecondSwitch;
      @NotNull private final NewSelector newSelector;
      @NotNull private final List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();

      JavacSwitchCandidate(@NotNull SwitchStatement firstSwitch,
                           @NotNull VarExprent firstSwitchSelector,
                           @NotNull VarExprent tmpVarInFirstSwitch,
                           @NotNull SwitchStatement secondSwitch,
                           @NotNull Map<Integer, String> mappedCaseLabelValues,
                           @NotNull List<Exprent> firstSwitchExprents,
                           @NotNull Exprent secondSwitchSelector,
                           @NotNull Statement firstStatementInSecondSwitch) {
        this.firstSwitch = firstSwitch;
        this.secondSwitch = secondSwitch;
        this.firstSwitchSelector = firstSwitchSelector;
        this.secondSwitchSelector = secondSwitchSelector;

        this.tmpVarInFirstSwitch = tmpVarInFirstSwitch;
        this.mappedCaseLabelValues = mappedCaseLabelValues;

        this.firstSwitchExprents = firstSwitchExprents;
        this.firstStatementInSecondSwitch = firstStatementInSecondSwitch;

        newSelector = getSelector(tempVarAssignments, firstSwitchExprents);
      }

      @Override
      public void simplify() {

        for (List<Exprent> values : secondSwitch.getCaseValues()) {
          for (int i = 0; i < values.size(); i++) {
            ConstExprent constExprent = (ConstExprent)values.get(i);
            if (constExprent == null) continue;
            String labelValue = mappedCaseLabelValues.get(constExprent.getIntValue());
            values.set(i, new ConstExprent(VARTYPE_STRING, labelValue, null));
          }
        }

        List<Exprent> newExprents;
        if (newSelector.lastExprentIndex() > 0) {
          newExprents = List.copyOf(firstSwitchExprents.subList(0, newSelector.lastExprentIndex()));
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
        List<Exprent> exprents = firstStatementInSecondSwitch.getExprents();
        if (exprents != null) {
          exprents.addAll(0, newExprents);
        }
        secondSwitchSelector.replaceExprent(((SwitchExprent)secondSwitchSelector).getValue(), newSelector.newSelector());
      }

      @Override
      public Set<SwitchStatement> usedSwitch() {
        return Set.of(firstSwitch, secondSwitch);
      }

      @NotNull
      private NewSelector getSelector(@NotNull List<TempVarAssignmentItem> tempVarAssignments, List<Exprent> firstSwitchExprents) {
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
        return new NewSelector(lastExprentIndex, newSelector);
      }

      @Override
      public List<TempVarAssignmentItem> prepareTempAssignments() {
        return tempVarAssignments;
      }

      private record NewSelector(int lastExprentIndex, Exprent newSelector) {
      }
    }

    private static class EcjSwitchCandidate extends SwitchOnStringCandidate {
      @NotNull private final SwitchStatement switchStatement;
      @NotNull private final Exprent switchSelector;
      @NotNull private final Map<Integer, @NotNull IfStatement> mappedIfStatements;
      @NotNull private final Map<Integer, @NotNull String> mappedCaseLabelValues;
      @NotNull private final List<TempVarAssignmentItem> tempVarAssignments = new ArrayList<>();

      private EcjSwitchCandidate(@NotNull SwitchStatement switchStatement,
                                 @NotNull Exprent switchSelector,
                                 @NotNull VarExprent tmpVar,
                                 @NotNull Map<Integer, IfStatement> mappedIfStatements,
                                 @NotNull Map<Integer, String> mappedCaseLabelValues) {
        this.switchStatement = switchStatement;
        this.switchSelector = switchSelector;
        this.mappedIfStatements = mappedIfStatements;
        this.mappedCaseLabelValues = mappedCaseLabelValues;
        this.tempVarAssignments.add(new TempVarAssignmentItem(tmpVar, switchStatement));
      }

      @Override
      public void simplify() {
        Exprent switchSelector = switchStatement.getHeadExprent();
        if (switchSelector == null || switchSelector.type != Exprent.EXPRENT_SWITCH) return;
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

      @Override
      public Set<SwitchStatement> usedSwitch() {
        return Set.of(switchStatement);
      }

      @Override
      public List<TempVarAssignmentItem> prepareTempAssignments() {
        return tempVarAssignments;
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
