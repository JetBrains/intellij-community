/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

// Not really with identical branches, but also common parts
public class IfStatementWithIdenticalBranchesInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement ifStatement) {
        PsiStatement[] thenStatements = unwrap(ifStatement.getThenBranch());
        PsiStatement[] elseStatements = unwrap(ifStatement.getElseBranch());
        final boolean mayChangeSemantics;
        final CommonPartType type;
        boolean forceInfo = false;
        ImplicitElse implicitElse = ImplicitElse.from(thenStatements, elseStatements, ifStatement);
        if (implicitElse != null) {
          mayChangeSemantics = false;
          type = implicitElse.getType();
        }
        else {
          ThenElse thenElse = ThenElse.from(ifStatement, thenStatements, elseStatements, isOnTheFly);
          if (thenElse == null) {
            ElseIf elseIf = ElseIf.from(ifStatement, thenStatements);
            if (elseIf == null) return;
            if (!isOnTheFly) return;
            String message = InspectionsBundle.message("inspection.common.if.parts.family.else.if");
            holder.registerProblem(ifStatement.getChildren()[0], message, ProblemHighlightType.INFORMATION, new MergeElseIfsFix());
            return;
          }
          if (!(ifStatement.getParent() instanceof PsiCodeBlock)) {
            forceInfo = true;
            if (!isOnTheFly) return;
          }
          type = thenElse.myCommonPartType;
          mayChangeSemantics = thenElse.myMayChangeSemantics;
        }
        boolean warning = !forceInfo
                          && type != CommonPartType.WITH_VARIABLES_EXTRACT
                          && !mayChangeSemantics;
        if (!isOnTheFly && !warning) return;
        ProblemHighlightType highlightType = warning ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
        String message = type.getMessage(mayChangeSemantics);
        PsiElement element = warning ? ifStatement.getChildren()[0] : ifStatement;
        holder.registerProblem(element, message, highlightType, new ExtractCommonIfPartsFix(type, mayChangeSemantics, isOnTheFly));
      }
    };
  }


  private static class MergeElseIfsFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.common.if.parts.family.else.if");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.common.if.parts.family.else.if");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiIfStatement ifStatement = tryCast(descriptor.getStartElement().getParent(), PsiIfStatement.class);
      if (ifStatement == null) return;
      ElseIf elseIf = ElseIf.from(ifStatement, unwrap(ifStatement.getThenBranch()));
      if (elseIf == null) return;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return;
      elseIf.myElseBranch.replace(elseIf.myElseIfElseStatement);

      String firstCondition = ParenthesesUtils.getText(condition, ParenthesesUtils.OR_PRECEDENCE);
      String secondCondition = ParenthesesUtils.getText(elseIf.myElseIfCondition, ParenthesesUtils.OR_PRECEDENCE);

      String newCondition = firstCondition + "||" + secondCondition;
      PsiReplacementUtil.replaceExpression(condition, newCondition);
    }
  }

  @Nullable
  private static ExtractionUnit extractHeadCommonStatement(@NotNull PsiStatement thenStmt,
                                                           @NotNull PsiStatement elseStmt,
                                                           @NotNull List<PsiLocalVariable> conditionVariables,
                                                           LocalEquivalenceChecker equivalence) {
    boolean equal = thenStmt instanceof PsiDeclarationStatement
                    ? equivalence.topLevelVarsAreEqualNotConsideringInitializers(thenStmt, elseStmt)
                    : equivalence.statementsAreEquivalent(thenStmt, elseStmt);
    if (!equal) return null;
    final boolean statementMayChangeSemantics;
    final boolean equivalent;
    final boolean mayInfluenceCondition;
    if (!(thenStmt instanceof PsiDeclarationStatement)) {
      statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenStmt, e -> false);
      equivalent = true;
      mayInfluenceCondition = mayInfluenceCondition(thenStmt, conditionVariables);
    }
    else {
      PsiLocalVariable thenVariable = extractVariable(thenStmt);
      PsiLocalVariable elseVariable = extractVariable(elseStmt);
      if (thenVariable == null || elseVariable == null) return null;
      PsiExpression thenInitializer = thenVariable.getInitializer();
      if (thenInitializer == null) return null;
      statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenInitializer, e -> false);
      mayInfluenceCondition = mayInfluenceCondition(thenInitializer, conditionVariables);
      equivalent = equivalence.expressionsAreEquivalent(thenInitializer, elseVariable.getInitializer());
    }
    return new ExtractionUnit(thenStmt, elseStmt, statementMayChangeSemantics, mayInfluenceCondition, equivalent);
  }

  private static boolean mayInfluenceCondition(@NotNull PsiElement element, @NotNull List<PsiLocalVariable> conditionVariables) {
    return StreamEx.ofTree(element, e -> StreamEx.of(e.getChildren()))
      .select(PsiReferenceExpression.class)
      .map(expression -> expression.resolve())
      .nonNull()
      .select(PsiLocalVariable.class)
      .anyMatch(el -> conditionVariables.contains(el));
  }


  private static class ExtractCommonIfPartsFix implements LocalQuickFix {
    private final CommonPartType myType;
    private final boolean myMayChangeSemantics;
    private final boolean myIsOnTheFly;

    private ExtractCommonIfPartsFix(CommonPartType type, boolean semantics, boolean isOnTheFly) {
      myType = type;
      myMayChangeSemantics = semantics;
      myIsOnTheFly = isOnTheFly;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.common.if.parts.family");
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myType.getMessage(myMayChangeSemantics);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class, false);
      if (ifStatement == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (!(ifStatement.getParent() instanceof PsiCodeBlock)) {
        ifStatement = BlockUtils.expandSingleStatementToBlockStatement(ifStatement);
      }
      PsiStatement[] thenStatements = unwrap(ifStatement.getThenBranch());
      PsiStatement[] elseStatements = unwrap(ifStatement.getElseBranch());
      if (tryApplyThenElseFix(ifStatement, factory, thenStatements, elseStatements)) return;
      applyImplicitElseFix(ifStatement, thenStatements, elseStatements);
    }

    private static void applyImplicitElseFix(PsiIfStatement ifStatement, PsiStatement[] thenStatements, PsiStatement[] elseStatements) {
      ImplicitElse implicitElse = ImplicitElse.from(thenStatements, elseStatements, ifStatement);
      if (implicitElse == null) return;
      PsiIfStatement ifToDelete = implicitElse.myIfToDelete;
      CommentTracker ct = new CommentTracker();
      PsiElement parent = ifToDelete.getParent();
      if(ifToDelete == ifStatement) { // Only in this case condition may contains side effect
        PsiExpression condition = ifToDelete.getCondition();
        if(condition == null) return;
        ct.markUnchanged(condition);
        List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(condition);
        PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, condition);
        for (int statementIndex = sideEffectStatements.length - 1; statementIndex >= 0; statementIndex--) {
          PsiStatement statement = sideEffectStatements[statementIndex];
          parent.addAfter(statement, ifToDelete);
        }
      }
      ct.deleteAndRestoreComments(ifToDelete);
    }

    private boolean tryApplyThenElseFix(PsiIfStatement ifStatement,
                                        PsiElementFactory factory,
                                        PsiStatement[] thenStatements,
                                        PsiStatement[] elseStatements) {
      ThenElse thenElse = ThenElse.from(ifStatement, thenStatements, elseStatements, myIsOnTheFly);
      if (thenElse == null) return false;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (thenBranch == null || elseBranch == null) return false;
      if (!tryCleanUpHead(ifStatement, thenElse.myHeadUnitsOfThen, factory, thenElse.mySubstitutionTable)) return true;
      cleanUpTail(ifStatement, thenElse.myTailStatementsOfThen);
      boolean elseToDelete = ControlFlowUtils.unwrapBlock(elseBranch).length == 0;
      boolean thenToDelete = ControlFlowUtils.unwrapBlock(thenBranch).length == 0;
      if (thenToDelete && elseToDelete) {
        ifStatement.delete();
      }
      else if(thenElse.myCommonPartType != CommonPartType.WHOLE_BRANCH) {
        // this is possible when one branch can be removed but it contains comments
        return true;
      }
      else if (elseToDelete) {
        elseBranch.delete();
      }
      else if (thenToDelete) {
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null) return true;
        String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
        String newThenBranch = elseBranch.getText();
        ifStatement.replace(factory.createStatementFromText("if(" + negatedCondition + ")" + newThenBranch, ifStatement));
      }
      return true;
    }

    private static void bindNames(@NotNull PsiStatement statement, PsiVariable variable, String finalName) {
      ReferencesSearch.search(variable, new LocalSearchScope(statement)).forEach(reference -> {
        if (reference.getElement() instanceof PsiReferenceExpression) {
          ExpressionUtils.bindReferenceTo((PsiReferenceExpression)reference.getElement(), finalName);
        }
      });
      variable.setName(finalName);
    }

    private static boolean tryCleanUpHead(PsiIfStatement ifStatement,
                                          List<ExtractionUnit> units, PsiElementFactory factory,
                                          Map<PsiLocalVariable, String> substitutionTable) {
      PsiElement parent = ifStatement.getParent();
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if(elseBranch == null) return false;
      for (ExtractionUnit unit : units) {
        PsiStatement thenStatement = unit.getThenStatement();
        PsiStatement elseStatement = unit.getElseStatement();
        if (thenStatement instanceof PsiDeclarationStatement) {
          PsiExpression thenInitializer = extractInitializer(thenStatement);
          PsiExpression elseInitializer = extractInitializer(elseStatement);
          PsiVariable thenVariable = extractVariable(thenStatement);
          PsiLocalVariable elseVariable = extractVariable(elseStatement);
          if(thenVariable == null || elseVariable == null) return false;
          String thenVariableTypeText = thenVariable.getType().getCanonicalText();
          PsiModifierList thenModifierList = thenVariable.getModifierList();
          String modifiers;
          modifiers = thenModifierList == null || thenModifierList.getText().isEmpty() ? "" : thenModifierList.getText() + " ";
          String thenNameToReplaceInElse = substitutionTable.get(elseVariable);
          String varName = thenNameToReplaceInElse != null ? thenNameToReplaceInElse : thenVariable.getName();

          if(thenNameToReplaceInElse != null) {
            JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(ifStatement.getProject());
            varName = manager.suggestUniqueVariableName(thenNameToReplaceInElse, ifStatement, var -> PsiTreeUtil.isAncestor(ifStatement, var, false));
            if (!thenNameToReplaceInElse.equals(elseVariable.getName())) {
              thenStatement = replaceName(ifStatement, factory, thenStatement, thenVariable, varName, modifiers);
              bindNames(elseBranch, elseVariable, varName);
            }
          }

          if (!unit.hasEquivalentStatements()) {

            String variableDeclaration = modifiers + thenVariableTypeText + " " + varName + ";";
            PsiStatement varDeclarationStmt = factory.createStatementFromText(variableDeclaration, parent);
            parent.addBefore(varDeclarationStmt, ifStatement);

            replaceWithDeclarationIfNeeded(ifStatement, factory, thenStatement, thenInitializer, varName);
            replaceWithDeclarationIfNeeded(ifStatement, factory, elseStatement, elseInitializer, varName);
            continue;
          }
        }
        parent.addBefore(thenStatement.copy(), ifStatement);
        thenStatement.delete();
        elseStatement.delete();
      }
      return true;
    }

    private static PsiStatement replaceName(PsiIfStatement ifStatement,
                                            PsiElementFactory factory,
                                            PsiStatement thenStatement,
                                            PsiVariable variable, String varName,
                                            String modifiers) {
      ReferencesSearch.search(variable, new LocalSearchScope(ifStatement)).forEach(reference -> {
        if (reference.getElement() instanceof PsiReferenceExpression) {
          ExpressionUtils.bindReferenceTo((PsiReferenceExpression)reference.getElement(), varName);
        }
      });
      String maybeInitializer = variable.getInitializer() == null ? "" : "=" + variable.getInitializer().getText();
      String text = modifiers + variable.getType().getCanonicalText() + " " + varName + maybeInitializer + ";";
      PsiStatement variableDeclaration =
        factory.createStatementFromText(text, null);
      thenStatement = (PsiStatement)thenStatement.replace(variableDeclaration);
      return thenStatement;
    }

    private static void cleanUpTail(@NotNull PsiIfStatement ifStatement, @NotNull List<PsiStatement> tailStatements) {
      if (!tailStatements.isEmpty()) {
        for (PsiStatement statement : tailStatements) {
          ifStatement.getParent().addAfter(statement.copy(), ifStatement);
        }
        PsiStatement[] thenStatements = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch());
        PsiStatement[] elseStatements = ControlFlowUtils.unwrapBlock(ifStatement.getElseBranch());
        int thenLength = thenStatements.length;
        int elseLength = elseStatements.length;
        for (int i = 0; i < tailStatements.size(); i++) {
          thenStatements[thenLength - 1 - i].delete();
          elseStatements[elseLength - 1 - i].delete();
        }
      }
    }

    private static void replaceWithDeclarationIfNeeded(PsiIfStatement ifStatement,
                                                       PsiElementFactory factory,
                                                       PsiStatement statement,
                                                       PsiExpression initializer,
                                                       String varName) {
      if (initializer != null) {
        PsiStatement assignment = factory.createStatementFromText(varName + "=" + initializer.getText() + ";", ifStatement);
        statement.replace(assignment);
      }
    }

    @Nullable
    private static PsiExpression extractInitializer(@Nullable PsiStatement statement) {
      PsiVariable variable = extractVariable(statement);
      if (variable == null) return null;
      return variable.getInitializer();
    }
  }

  @Nullable
  private static PsiLocalVariable extractVariable(@Nullable PsiStatement statement) {
    PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
    if (declarationStatement == null) return null;
    PsiElement[] elements = declarationStatement.getDeclaredElements();
    if (elements.length != 1) return null;
    return tryCast(elements[0], PsiLocalVariable.class);
  }

  private static class ExtractionUnit {
    private final boolean myMayChangeSemantics;
    private final boolean myMayInfluenceCondition;
    private final @NotNull PsiStatement myThenStatement;
    private final @NotNull PsiStatement myElseStatement;
    private final boolean myIsEquivalent;


    private ExtractionUnit(@NotNull PsiStatement thenStatement,
                           @NotNull PsiStatement elseStatement,
                           boolean mayChangeSemantics,
                           boolean mayInfluenceCondition, boolean isEquivalent) {
      myMayChangeSemantics = mayChangeSemantics;
      myThenStatement = thenStatement;
      myElseStatement = elseStatement;
      myMayInfluenceCondition = mayInfluenceCondition;
      myIsEquivalent = isEquivalent;
    }

    public boolean haveSideEffects() {
      return myMayChangeSemantics;
    }

    @NotNull
    public PsiStatement getThenStatement() {
      return myThenStatement;
    }

    @NotNull
    public PsiStatement getElseStatement() {
      return myElseStatement;
    }

    public boolean hasEquivalentStatements() {
      return myIsEquivalent;
    }

    public boolean mayInfluenceCondition() {
      return myMayInfluenceCondition;
    }
  }


  private enum CommonPartType {
    VARIABLES_ONLY("inspection.common.if.parts.message.variables.only"),
    WITH_VARIABLES_EXTRACT("inspection.common.if.parts.message.with.variables.extract"),
    WITHOUT_VARIABLES_EXTRACT("inspection.common.if.parts.message.without.variables.extract"),
    WHOLE_BRANCH("inspection.common.if.parts.message.whole.branch"),
    COMPLETE_DUPLICATE("inspection.common.if.parts.message.complete.duplicate"),
    EXTRACT_SIDE_EFFECTS("inspection.common.if.parts.message.complete.duplicate.side.effect");

    private @NotNull final String myBundleKey;

    @NotNull
    private String getMessage(boolean mayChangeSemantics) {
      String mayChangeSemanticsText = mayChangeSemantics ? "(may change semantics)" : "";
      return InspectionsBundle.message(myBundleKey, mayChangeSemanticsText);
    }

    CommonPartType(@NotNull String key) {myBundleKey = key;}
  }

  @Nullable
  private static PsiIfStatement getEnclosingIfStmt(@NotNull PsiIfStatement ifStatement) {
    PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiIfStatement) {
      return (PsiIfStatement)parent;
    }
    if (parent instanceof PsiCodeBlock) {
      if (((PsiCodeBlock)parent).getStatements()[0] != ifStatement) return null;
      return tryCast(parent.getParent().getParent(), PsiIfStatement.class);
    }
    return null;
  }

  private static boolean isMeaningful(@NotNull PsiStatement statement) {
    if (statement instanceof PsiEmptyStatement) return false;
    if (statement instanceof PsiBlockStatement) {
      return ((PsiBlockStatement)statement).getCodeBlock().getStatements().length != 0;
    }
    return true;
  }

  private static class ImplicitElseData {
    final @NotNull List<PsiStatement> myImplicitElseStatements;
    final @NotNull PsiIfStatement myIfWithImplicitElse;

    private ImplicitElseData(@NotNull List<PsiStatement> implicitElseStatements, @NotNull PsiIfStatement ifWithImplicitElse) {
      myImplicitElseStatements = implicitElseStatements;
      myIfWithImplicitElse = ifWithImplicitElse;
    }
  }

  /**
   * detects
   * if(c1) {
   *   if(c2) {
   *     ...commonStatements
   *   }
   * }
   * ...commonStatements
   */
  @Nullable
  private static ImplicitElseData getIfWithImplicitElse(@NotNull PsiIfStatement ifStatement,
                                                        @NotNull PsiStatement[] thenStatements,
                                                        boolean returnsNothing) {
    int statementsLength = thenStatements.length;
    if (statementsLength == 0) return null;
    PsiIfStatement currentIf = ifStatement;
    List<PsiStatement> statements = new ArrayList<>();
    int count = 0;
    boolean conditionHasSideEffects = false;
    while (true) {
      if (currentIf.getElseBranch() != null) return null;
      if (currentIf != ifStatement && ControlFlowUtils.unwrapBlock(currentIf.getThenBranch()).length != 1) break;
      if(currentIf.getCondition() != null && SideEffectChecker.mayHaveSideEffects(currentIf.getCondition())) {
        conditionHasSideEffects = true;
      }
      PsiStatement sibling = currentIf;
      do {
        sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class);
        if (sibling == null) break;
        if (!isMeaningful(sibling)) continue;
        count++;
        statements.add(sibling);
      }
      while (count <= statementsLength);
      if (!statements.isEmpty()) break;

      PsiIfStatement enclosingIf = getEnclosingIfStmt(currentIf);
      if (enclosingIf == null) break;
      currentIf = enclosingIf;
    }
    if(conditionHasSideEffects && ifStatement != currentIf) return null;
    // ensure it is last statements in method
    PsiElement parent = currentIf.getParent();
    if (!(parent instanceof PsiCodeBlock)) return null;
    if (!(parent.getParent() instanceof PsiMethod)) return null;
    if (!statements.isEmpty()) {
      if (PsiTreeUtil.getNextSiblingOfType(statements.get(statements.size() - 1), PsiStatement.class) != null) return null;
    }
    if (returnsNothing) {
      // skip possible return;
      if (count == statementsLength || count == statementsLength - 1) return new ImplicitElseData(statements, currentIf);
    }
    else {
      if (count == statementsLength) return new ImplicitElseData(statements, currentIf);
    }
    return null;
  }

  private static void addLocalVariables(Set<PsiLocalVariable> variables, List<PsiStatement> statements) {
    for (PsiStatement statement : statements) {
      addVariables(variables, statement);
    }
  }

  private static void addVariables(Set<PsiLocalVariable> variables, PsiStatement statement) {
    PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
    if (declarationStatement == null) return;
    for (PsiElement element : declarationStatement.getDeclaredElements()) {
      if (element instanceof PsiLocalVariable) {
        variables.add((PsiLocalVariable)element);
      }
    }
  }

  @NotNull
  private static PsiStatement[] unwrap(@Nullable PsiStatement statement) {
    PsiBlockStatement block = tryCast(statement, PsiBlockStatement.class);
    if (block != null) {
      return Arrays.stream(block.getCodeBlock().getStatements()).filter(IfStatementWithIdenticalBranchesInspection::isMeaningful).collect(Collectors.toList())
        .toArray(PsiStatement.EMPTY_ARRAY);
    }
    return statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[]{statement};
  }

  private static class ImplicitElse {
    final @NotNull PsiIfStatement myIfToDelete;

    private ImplicitElse(@NotNull PsiIfStatement ifToDelete) {
      myIfToDelete = ifToDelete;
    }

    @Nullable
    static ImplicitElse from(@NotNull PsiStatement[] thenBranch,
                             @NotNull PsiStatement[] elseBranch,
                             @NotNull PsiIfStatement ifStatement) {
      if (elseBranch.length != 0 || thenBranch.length == 0) return null;
      PsiStatement lastThenStatement = thenBranch[thenBranch.length - 1];
      if (!(lastThenStatement instanceof PsiReturnStatement)) return null;
      boolean returnsNothing = ((PsiReturnStatement)lastThenStatement).getReturnValue() == null;
      ImplicitElseData implicitElse = getIfWithImplicitElse(ifStatement, thenBranch, returnsNothing);
      if (implicitElse == null) return null;
      if (implicitElse.myImplicitElseStatements.isEmpty()) return null;
      if (implicitElse.myImplicitElseStatements.size() == 1) {
        PsiStatement statement = implicitElse.myImplicitElseStatements.get(0);
        if (statement instanceof PsiReturnStatement) {
          if (((PsiReturnStatement)statement).getReturnValue() == null) return null;
        }
      }
      List<PsiStatement> elseStatements = implicitElse.myImplicitElseStatements;
      Set<PsiLocalVariable> variables = new HashSet<>();
      List<PsiStatement> thenStatements = new ArrayList<>(Arrays.asList(thenBranch));
      addLocalVariables(variables, thenStatements);
      addLocalVariables(variables, implicitElse.myImplicitElseStatements);
      if (!branchesAreEquivalent(thenBranch, elseStatements, new LocalEquivalenceChecker(variables))) return null;
      return new ImplicitElse(implicitElse.myIfWithImplicitElse);
    }

    CommonPartType getType() {
      PsiExpression condition = myIfToDelete.getCondition();
      if(condition != null) {
        if (SideEffectChecker.mayHaveSideEffects(condition)) {
          return CommonPartType.EXTRACT_SIDE_EFFECTS;
        }
      }
      return CommonPartType.COMPLETE_DUPLICATE;
    }
  }

  private static class ThenElse {
    // count of statements required to consider branch consists of similar statements
    public static final int SIMILAR_STATEMENTS_COUNT = 2;
    final List<ExtractionUnit> myHeadUnitsOfThen;
    final List<PsiStatement> myTailStatementsOfThen;
    final boolean myMayChangeSemantics;
    final CommonPartType myCommonPartType;
    final Map<PsiLocalVariable, String> mySubstitutionTable;

    private ThenElse(List<ExtractionUnit> headUnitsOfThen,
                     List<PsiStatement> tailUnitsOfThen,
                     boolean mayChangeSemantics,
                     CommonPartType commonPartType,
                     Map<PsiLocalVariable, String> substitutionTable) {
      myHeadUnitsOfThen = headUnitsOfThen;
      myTailStatementsOfThen = tailUnitsOfThen;
      myMayChangeSemantics = mayChangeSemantics;
      myCommonPartType = commonPartType;
      mySubstitutionTable = substitutionTable;
    }


    @Contract("true, _, _ -> true")
    private static boolean mayChangeSemantics(boolean conditionHasSideEffects,
                                              boolean conditionVariablesCantBeChangedTransitively,
                                              List<ExtractionUnit> headCommonParts) {
      if (conditionHasSideEffects) return true;
      if (conditionVariablesCantBeChangedTransitively) {
        return !headCommonParts.isEmpty() && StreamEx.of(headCommonParts)
          .anyMatch(unit -> unit.mayInfluenceCondition() && !(unit.getThenStatement() instanceof PsiDeclarationStatement));
      }
      return !headCommonParts.isEmpty() && StreamEx.of(headCommonParts)
        .anyMatch(unit -> unit.haveSideEffects() && !(unit.getThenStatement() instanceof PsiDeclarationStatement));
    }

    @NotNull
    private static LocalEquivalenceChecker getChecker(PsiStatement[] thenBranch, PsiStatement[] elseBranch) {
      Set<PsiLocalVariable> localVariables = new HashSet<>();
      addLocalVariables(localVariables, Arrays.asList(thenBranch));
      addLocalVariables(localVariables, Arrays.asList(elseBranch));
      return new LocalEquivalenceChecker(localVariables);
    }

    @NotNull
    private static CommonPartType getType(@NotNull List<ExtractionUnit> headStatements,
                                          List<PsiStatement> tailStatements,
                                          boolean declarationsAreEquivalent,
                                          int thenLen,
                                          int elseLen,
                                          @NotNull PsiStatement thenBranch,
                                          @NotNull PsiStatement elseBranch) {
      if (declarationsAreEquivalent) {
        int duplicatedStatements = headStatements.size() + tailStatements.size();
        if (thenLen == duplicatedStatements && elseLen == duplicatedStatements) {
          return CommonPartType.COMPLETE_DUPLICATE;
        }
        if (canRemoveBranch(thenLen, thenBranch, duplicatedStatements)) return CommonPartType.WHOLE_BRANCH;
        if (canRemoveBranch(elseLen, elseBranch, duplicatedStatements)) return CommonPartType.WHOLE_BRANCH;
      }
      boolean hasVariables = false;
      boolean hasNonVariables = false;
      for (ExtractionUnit unit : headStatements) {
        if (unit.getThenStatement() instanceof PsiDeclarationStatement) {
          hasVariables = true;
        }
        else {
          hasNonVariables = true;
        }
        if (hasVariables && hasNonVariables) break;
      }
      if (!(hasVariables && hasNonVariables)) {
        for (PsiStatement statement : tailStatements) {
          if (statement instanceof PsiDeclarationStatement) {
            hasVariables = true;
          }
          else {
            hasNonVariables = true;
          }
          if (hasVariables && hasNonVariables) break;
        }
      }
      if (hasVariables && hasNonVariables) {
        return CommonPartType.WITH_VARIABLES_EXTRACT;
      }
      if (hasVariables) {
        return CommonPartType.VARIABLES_ONLY;
      }
      return CommonPartType.WITHOUT_VARIABLES_EXTRACT;
    }

    private static boolean canRemoveBranch(int len, PsiStatement branch, int duplicatedStatementsLen) {
      if (len == duplicatedStatementsLen) {
        PsiBlockStatement blockStatement = tryCast(branch, PsiBlockStatement.class);
        if (blockStatement != null && PsiTreeUtil.getChildOfType(blockStatement.getCodeBlock(), PsiComment.class) == null) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    static ThenElse from(@NotNull PsiIfStatement ifStatement,
                         @NotNull PsiStatement[] thenBranch,
                         @NotNull PsiStatement[] elseBranch,
                         boolean isOnTheFly) {
      LocalEquivalenceChecker equivalence = getChecker(thenBranch, elseBranch);

      int thenLen = thenBranch.length;
      int elseLen = elseBranch.length;
      int minStmtCount = Math.min(thenLen, elseLen);

      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;

      boolean conditionHasSideEffects = SideEffectChecker.mayHaveSideEffects(condition);
      if (!isOnTheFly && conditionHasSideEffects) return null;
      List<PsiLocalVariable> conditionVariables = new ArrayList<>();
      boolean conditionVariablesCantBeChangedTransitively = StreamEx.ofTree(((PsiElement)condition), el -> StreamEx.of(el.getChildren()))
        .allMatch(element -> {
          if (element instanceof PsiReferenceExpression) {
            PsiLocalVariable localVariable = tryCast(((PsiReferenceExpression)element).resolve(), PsiLocalVariable.class);
            if (localVariable == null) return false;
            conditionVariables.add(localVariable);
          }
          else return !(element instanceof PsiMethodCallExpression);
          return true;
        });

      List<ExtractionUnit> headCommonParts = new ArrayList<>();
      Set<PsiVariable> extractedVariables = new com.intellij.util.containers.HashSet<>();
      Set<PsiVariable> notEquivalentVariableDeclarations = new com.intellij.util.containers.HashSet<>(0);

      extractHeadCommonParts(thenBranch, elseBranch, isOnTheFly, equivalence, minStmtCount, conditionVariables, headCommonParts,
                             extractedVariables, notEquivalentVariableDeclarations);
      if(headCommonParts.isEmpty()) {
        // when head common parts contains e.g. first variable with different names but changes semantics and batch mode is on
        equivalence.mySubstitutionTable.clear();
      }

      int extractedFromStart = headCommonParts.size();
      int canBeExtractedFromThenTail = thenLen - extractedFromStart;
      int canBeExtractedFromElseTail = elseLen - extractedFromStart;
      int canBeExtractedFromTail = Math.min(canBeExtractedFromThenTail, canBeExtractedFromElseTail);
      List<PsiStatement> tailCommonParts = new ArrayList<>();
      extractTailCommonParts(ifStatement, thenBranch, elseBranch, equivalence, thenLen, elseLen, extractedVariables, canBeExtractedFromTail,
                             tailCommonParts);

      tryAppendHeadPartsToTail(headCommonParts, canBeExtractedFromThenTail, canBeExtractedFromElseTail, canBeExtractedFromTail,
                               tailCommonParts);

      Map<PsiLocalVariable, String> substitutionTable = equivalence.mySubstitutionTable;
      if (uncommonElseStatementsContainsThenNames(elseBranch, elseLen, headCommonParts, tailCommonParts, substitutionTable)) {
        return null;
      }
      if (headCommonParts.isEmpty() && tailCommonParts.isEmpty()) return null;
      PsiStatement thenStatement = ifStatement.getThenBranch();
      PsiStatement elseStatement = ifStatement.getElseBranch();
      if (thenStatement == null || elseStatement == null) return null;
      final CommonPartType type = getType(headCommonParts, tailCommonParts, notEquivalentVariableDeclarations.isEmpty(), thenLen, elseLen,
                                          thenStatement, elseStatement);
      if (type == CommonPartType.VARIABLES_ONLY && !notEquivalentVariableDeclarations.isEmpty()) return null;
      boolean mayChangeSemantics =
        mayChangeSemantics(conditionHasSideEffects, conditionVariablesCantBeChangedTransitively, headCommonParts);
      return new ThenElse(headCommonParts, tailCommonParts, mayChangeSemantics, type, substitutionTable);
    }

    private static void tryAppendHeadPartsToTail(List<ExtractionUnit> headCommonParts,
                                                 int canBeExtractedFromThenTail,
                                                 int canBeExtractedFromElseTail,
                                                 int canBeExtractedFromTail,
                                                 List<PsiStatement> tailCommonParts) {
      if (canBeExtractedFromTail == tailCommonParts.size() && canBeExtractedFromElseTail == canBeExtractedFromThenTail) {
        // trying to append to tail statements, that may change semantics from head, because in tail they can't change semantics
        for (int i = headCommonParts.size() - 1; i >= 0; i--) {
          ExtractionUnit unit = headCommonParts.get(i);
          PsiStatement thenStatement = unit.getThenStatement();
          if (!unit.haveSideEffects() || !unit.hasEquivalentStatements()) break;
          headCommonParts.remove(i);
          tailCommonParts.add(thenStatement);
        }
      }
    }

    private static void extractTailCommonParts(@NotNull PsiIfStatement ifStatement,
                                               @NotNull PsiStatement[] thenBranch,
                                               @NotNull PsiStatement[] elseBranch,
                                               LocalEquivalenceChecker equivalence,
                                               int thenLen,
                                               int elseLen,
                                               Set<PsiVariable> extractedVariables,
                                               int canBeExtractedFromTail,
                                               List<PsiStatement> tailCommonParts) {
      if (!isSimilarTailStatements(thenBranch)) {
        for (int i = 0; i < canBeExtractedFromTail; i++) {
          PsiStatement thenStmt = thenBranch[thenLen - i - 1];
          PsiStatement elseStmt = elseBranch[elseLen - i - 1];
          if (equivalence.statementsAreEquivalent(thenStmt, elseStmt)) {
            boolean canBeExtractedOutOfIf = VariableAccessUtils.collectUsedVariables(thenStmt).stream()
              .filter(var -> var instanceof PsiLocalVariable)
              .filter(var -> PsiTreeUtil.isAncestor(ifStatement, var, false))
              .allMatch(var -> extractedVariables.contains(var));
            if (!canBeExtractedOutOfIf) break;
            tailCommonParts.add(thenStmt);
          }
          else {
            break;
          }
        }
      }
    }

    private static void extractHeadCommonParts(@NotNull PsiStatement[] thenBranch,
                                               @NotNull PsiStatement[] elseBranch,
                                               boolean isOnTheFly,
                                               LocalEquivalenceChecker equivalence,
                                               int minStmtCount,
                                               List<PsiLocalVariable> conditionVariables,
                                               List<ExtractionUnit> headCommonParts,
                                               Set<PsiVariable> extractedVariables,
                                               Set<PsiVariable> notEquivalentVariableDeclarations) {
      if (!isSimilarHeadStatements(thenBranch)) {
        for (int i = 0; i < minStmtCount; i++) {
          PsiStatement thenStmt = thenBranch[i];
          PsiStatement elseStmt = elseBranch[i];
          ExtractionUnit unit = extractHeadCommonStatement(thenStmt, elseStmt, conditionVariables, equivalence);
          if (unit == null) break;
          if (!isOnTheFly && unit.haveSideEffects()) break;
          boolean dependsOnVariableWithNonEquivalentInitializer = VariableAccessUtils.collectUsedVariables(thenStmt).stream()
            .filter(var -> var instanceof PsiLocalVariable)
            .anyMatch(var -> notEquivalentVariableDeclarations.contains(var));

          if (dependsOnVariableWithNonEquivalentInitializer) {
            break;
          }
          PsiVariable variable = extractVariable(unit.getThenStatement());
          if (variable != null) {
            extractedVariables.add(variable);
            if (!unit.hasEquivalentStatements()) {
              notEquivalentVariableDeclarations.add(variable);
            }
          }
          headCommonParts.add(unit);
        }
      }
    }


    /**
     * Heuristic detecting that removing duplication can decrease beauty of the code
     */
    private static boolean isSimilarHeadStatements(@NotNull PsiStatement[] thenBranch) {
      if (thenBranch.length <= SIMILAR_STATEMENTS_COUNT) return false;
      PsiExpressionStatement expressionStatement = tryCast(thenBranch[0], PsiExpressionStatement.class);
      if (expressionStatement == null) return false;
      PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return false;
      for (int i = thenBranch.length - 1; i >= 0; i--) {
        if (!isSimilarCall(thenBranch[i], call)) return false;
      }
      return true;
    }

    private static boolean isSimilarTailStatements(@NotNull PsiStatement[] thenBranch) {
      if (thenBranch.length <= SIMILAR_STATEMENTS_COUNT) return false;
      PsiExpressionStatement expressionStatement = tryCast(thenBranch[thenBranch.length - 1], PsiExpressionStatement.class);
      if (expressionStatement == null) return false;
      PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return false;
      for (int i = thenBranch.length - 1; i >= 0; i--) {
        if (!isSimilarCall(thenBranch[i], call)) return false;
      }
      return true;
    }

    private static boolean isSimilarCall(PsiStatement statement, PsiMethodCallExpression call) {
      PsiExpressionStatement currentStatement = tryCast(statement, PsiExpressionStatement.class);
      if (currentStatement == null) return false;
      PsiMethodCallExpression otherCall = tryCast(currentStatement.getExpression(), PsiMethodCallExpression.class);
      if (otherCall == null) return false;
      return isEqualChain(call, otherCall);
    }

    /**
     * equals on chain of methods not considering argument lists, just method names
     */
    private static boolean isEqualChain(@Nullable PsiExpression first, @Nullable PsiExpression second) {
      if(first == null && second == null) return true;
      if(first == null || second == null) return false;
      PsiMethodCallExpression firstCall = tryCast(first, PsiMethodCallExpression.class);
      PsiMethodCallExpression secondCall = tryCast(second, PsiMethodCallExpression.class);
      PsiExpression firstCurrent = first;
      PsiExpression secondCurrent = second;
      while (firstCall != null && secondCall != null) {
        String firstName = firstCall.getMethodExpression().getReferenceName();
        String secondName = secondCall.getMethodExpression().getReferenceName();
        if(firstName == null || !firstName.equals(secondName)) return false;

        firstCurrent = firstCall.getMethodExpression().getQualifierExpression();
        secondCurrent = secondCall.getMethodExpression().getQualifierExpression();
        firstCall = tryCast(firstCurrent, PsiMethodCallExpression.class);
        secondCall = tryCast(secondCurrent, PsiMethodCallExpression.class);
      }
      return firstCurrent == null && secondCurrent == null ||
             EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstCurrent, secondCurrent);
    }

    private static boolean uncommonElseStatementsContainsThenNames(@NotNull PsiStatement[] elseBranch,
                                                                   int elseLen,
                                                                   List<ExtractionUnit> headCommonParts,
                                                                   List<PsiStatement> tailCommonParts,
                                                                   Map<PsiLocalVariable, String> substitutionTable) {
      if (!substitutionTable.isEmpty()) {
        int firstTailCommonStatementIndex = elseLen - tailCommonParts.size();
        com.intellij.util.containers.HashSet<String> names = new com.intellij.util.containers.HashSet<>(substitutionTable.values());
        for (int i = headCommonParts.size(); i < firstTailCommonStatementIndex; i++) {
          if (StreamEx.ofTree((PsiElement)elseBranch[i], e -> StreamEx.of(e.getChildren()))
            .select(PsiVariable.class)
            .filter(var -> var instanceof PsiLocalVariable || var instanceof PsiParameter)
            .anyMatch(var -> names.contains(var.getName()))) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private static boolean branchesAreEquivalent(@NotNull PsiStatement[] thenBranch,
                                               @NotNull List<PsiStatement> statements,
                                               @NotNull EquivalenceChecker equivalence) {
    for (int i = 0, length = statements.size(); i < length; i++) {
      PsiStatement elseStmt = statements.get(i);
      PsiStatement thenStmt = thenBranch[i];
      if (!equivalence.statementsAreEquivalent(thenStmt, elseStmt)) return false;
    }
    return true;
  }

  private static class ElseIf {
    final @NotNull PsiStatement myElseBranch;
    final @NotNull PsiStatement myElseIfElseStatement;
    final @NotNull PsiExpression myElseIfCondition;
    final @NotNull Map<PsiLocalVariable, String> mySubstitutionTable;

    private ElseIf(@NotNull PsiStatement elseBranch,
                   @NotNull PsiStatement elseIfElseStatement,
                   @NotNull PsiExpression elseIfCondition,
                   @NotNull Map<PsiLocalVariable, String> table) {
      myElseBranch = elseBranch;
      myElseIfElseStatement = elseIfElseStatement;
      myElseIfCondition = elseIfCondition;
      mySubstitutionTable = table;
    }

    @Nullable
    static ElseIf from(@NotNull PsiIfStatement ifStatement, @NotNull PsiStatement[] thenStatements) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (ifStatement.getCondition() == null) return null;
      PsiIfStatement elseIf = tryCast(ControlFlowUtils.stripBraces(elseBranch), PsiIfStatement.class);
      if (elseIf == null) return null;
      PsiExpression elseIfCondition = elseIf.getCondition();
      if (elseIfCondition == null) return null;
      PsiStatement[] elseIfThen = ControlFlowUtils.unwrapBlock(elseIf.getThenBranch());
      PsiStatement elseIfElseBranch = elseIf.getElseBranch();
      if (elseIfElseBranch == null) return null;
      if (elseIfThen.length != thenStatements.length) return null;
      Set<PsiLocalVariable> variables = new com.intellij.util.containers.HashSet<>();
      addLocalVariables(variables, Arrays.asList(thenStatements));
      addLocalVariables(variables, Arrays.asList(elseIfThen));
      LocalEquivalenceChecker equivalence = new LocalEquivalenceChecker(variables);
      if (!branchesAreEquivalent(thenStatements, Arrays.asList(elseIfThen), equivalence)) return null;
      return new ElseIf(elseBranch, elseIfElseBranch, elseIfCondition, equivalence.mySubstitutionTable);
    }
  }

  private static class LocalEquivalenceChecker extends EquivalenceChecker {
    final Set<PsiLocalVariable> myLocalVariables;
    // From else variable to then variable name
    final Map<PsiLocalVariable, String> mySubstitutionTable = new HashMap<>(0); // supposed to use rare

    private LocalEquivalenceChecker(Set<PsiLocalVariable> variables) {myLocalVariables = variables;}

    public boolean topLevelVarsAreEqualNotConsideringInitializers(@NotNull PsiStatement first,
                                                                  @NotNull PsiStatement second) {
      PsiLocalVariable localVariable1 = extractVariable(first);
      PsiLocalVariable localVariable2 = extractVariable(second);
      if (localVariable1 == null || localVariable2 == null) return false;
      if (!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return false;
      }
      if (!equalNotConsideringInitializer(localVariable1, localVariable2)) return false;
      return true;
    }

    private boolean equalNotConsideringInitializer(@NotNull PsiLocalVariable localVariable1, @NotNull PsiLocalVariable localVariable2) {

      PsiModifierList firstModifierList = localVariable1.getModifierList();
      PsiModifierList secondModifierList = localVariable2.getModifierList();
      if (firstModifierList != null || secondModifierList != null) {
        if (firstModifierList == null || secondModifierList == null) {
          return false;
        }
        String firstModifierListText = firstModifierList.getText();
        String secondModifierListText = secondModifierList.getText();
        if (firstModifierListText != null && !firstModifierListText.equals(secondModifierListText)) {
          return false;
        }
      }
      PsiAnnotation[] firstAnnotations = localVariable1.getAnnotations();
      if (firstAnnotations.length != localVariable2.getAnnotations().length || firstAnnotations.length != 0) return false;
      PsiType firstType = localVariable1.getType();
      if (!firstType.equals(localVariable2.getType())) return false;
      String firstName = localVariable1.getName();
      String secondName = localVariable2.getName();
      if (firstName == null || !firstName.equals(secondName)) {
        mySubstitutionTable.put(localVariable2, firstName);
      }
      return true;
    }

    @Override
    protected Match localVariablesAreEquivalent(@NotNull PsiLocalVariable localVariable1,
                                                @NotNull PsiLocalVariable localVariable2) {
      if (!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return super.localVariablesAreEquivalent(localVariable1, localVariable2);
      }
      if (!equalNotConsideringInitializer(localVariable1, localVariable2)) return EXACT_MISMATCH;
      PsiExpression firstInitializer = localVariable1.getInitializer();
      PsiExpression secondInitializer = localVariable2.getInitializer();
      return expressionsMatch(firstInitializer, secondInitializer);
    }

    @Override
    protected Match referenceExpressionsMatch(PsiReferenceExpression first,
                                              PsiReferenceExpression second) {
      PsiElement firstElement = first.resolve();
      PsiElement secondElement = second.resolve();
      if (firstElement instanceof PsiLocalVariable &&
          secondElement instanceof PsiLocalVariable &&
          myLocalVariables.contains(firstElement) &&
          myLocalVariables.contains(secondElement)) {
        PsiLocalVariable secondVar = (PsiLocalVariable)secondElement;
        PsiLocalVariable firstVar = (PsiLocalVariable)firstElement;
        if (firstVar.getType().equals(secondVar.getType())) {
          String firstVarName = firstVar.getName();
          String secondVarName = secondVar.getName();
          if (firstVarName != null && secondVarName != null) {
            return firstVarName.equals(secondVarName) || firstVarName.equals(mySubstitutionTable.get(secondVar))
                   ? EXACT_MATCH
                   : EXACT_MISMATCH;
          }
        }
      }
      return super.referenceExpressionsMatch(first, second);
    }
  }
}
