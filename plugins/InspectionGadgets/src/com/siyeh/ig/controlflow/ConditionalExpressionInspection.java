/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConditionalExpressionInspection extends BaseInspection {

  private static final Logger LOG = Logger.getInstance(ConditionalExpressionInspection.class);

  @SuppressWarnings("PublicField")
  public boolean ignoreSimpleAssignmentsAndReturns = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreExpressionContext = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "conditional.expression.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("conditional.expression.option"), "ignoreSimpleAssignmentsAndReturns");
    panel.addCheckbox(InspectionGadgetsBundle.message("conditional.expression.expression.context.option"), "ignoreExpressionContext");
    return panel;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean quickFix = ((Boolean)infos[0]).booleanValue();
    if (!quickFix) {
      return null;
    }
    return new ReplaceWithIfFix();
  }

  private static class ReplaceWithIfFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("conditional.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiConditionalExpression)) {
        return;
      }
      PsiConditionalExpression expression = (PsiConditionalExpression)element;
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
      if (surrounder == null) return;
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      expression = (PsiConditionalExpression)result.getExpression();
      PsiStatement statement = result.getAnchor();

      final PsiVariable variable =
        statement instanceof PsiDeclarationStatement ? PsiTreeUtil.getParentOfType(expression, PsiVariable.class) : null;
      PsiExpression thenExpression = expression.getThenExpression();
      PsiExpression elseExpression = expression.getElseExpression();
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(expression.getCondition());
      CommentTracker tracker = new CommentTracker();
      String ifText = "if(" + (condition == null ? "" : tracker.text(condition)) + ");\nelse;";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(ifText, condition);
      if (variable != null) {
        final String name = variable.getName();
        PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
          return;
        }
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && 
            typeElement.isInferredType() && 
            PsiTypesUtil.replaceWithExplicitType(typeElement) == null) {
          return;
        }
        if (initializer instanceof PsiArrayInitializerExpression) {
          final int conditionIdx = ArrayUtilRt.find(((PsiArrayInitializerExpression)initializer).getInitializers(), expression);
          if (conditionIdx >= 0) {
            initializer = (PsiExpression)initializer.replace(
              CommonJavaRefactoringUtil.convertInitializerToNormalExpression(initializer, variable.getType()));
            final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
            LOG.assertTrue(arrayInitializer != null, initializer.getText());
            expression = (PsiConditionalExpression)arrayInitializer.getInitializers()[conditionIdx];
            thenExpression = expression.getThenExpression();
            elseExpression = expression.getElseExpression();
          }
        }
        String thenAssignment = name + "=" +
                                getReplacement(initializer, expression, thenExpression, tracker).getText() + ";";
        String elseAssignment = name + "=" +
                                getReplacement(initializer, expression, elseExpression, tracker).getText() + ";";
        ifStatement.setThenBranch(factory.createStatementFromText(thenAssignment, initializer));
        ifStatement.setElseBranch(factory.createStatementFromText(elseAssignment, initializer));
        tracker.delete(initializer);
        final PsiElement parent = statement.getParent();
        ifStatement = (PsiIfStatement)parent.addAfter(ifStatement, statement);
        tracker.insertCommentsBefore(ifStatement);
      }
      else {
        final boolean addBraces = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class, true, PsiStatement.class) != null;
        PsiStatement thenBranch = (PsiStatement)getReplacement(statement, expression, thenExpression, tracker);
        PsiStatement elseBranch = (PsiStatement)getReplacement(statement, expression, elseExpression, tracker);
        if (addBraces) {
          thenBranch = (PsiStatement)BlockUtils.expandSingleStatementToBlockStatement(thenBranch).getParent().getParent();
          elseBranch = (PsiStatement)BlockUtils.expandSingleStatementToBlockStatement(elseBranch).getParent().getParent();
        }
        ifStatement.setThenBranch(thenBranch);
        ifStatement.setElseBranch(elseBranch);
        ifStatement = (PsiIfStatement)tracker.replaceAndRestoreComments(statement, ifStatement);
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch instanceof PsiReturnStatement) {
        final PsiReturnStatement returnStatement = (PsiReturnStatement)elseBranch;
        final PsiExpression value = returnStatement.getReturnValue();
        if (value instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)value;
          if (parenthesizedExpression.getExpression() == null) {
            elseBranch.delete();
          }
        }
      }
      else if (elseBranch instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)elseBranch;
        final PsiExpression statementExpression = expressionStatement.getExpression();
        if (statementExpression instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)statementExpression;
          final PsiExpression rhs = assignmentExpression.getRExpression();

          if (rhs instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)rhs;
            if (parenthesizedExpression.getExpression() == null) {
              rhs.delete();
            }
          }
        }
      }
      ifStatement = (PsiIfStatement)CodeStyleManager.getInstance(project).reformat(
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(ifStatement));
      if (!ControlFlowUtils.statementMayCompleteNormally(ifStatement.getThenBranch())) {
        if (!(ifStatement.getParent() instanceof PsiCodeBlock)) {
          ifStatement = BlockUtils.expandSingleStatementToBlockStatement(ifStatement);
        }
        final PsiStatement resultingElse = ifStatement.getElseBranch();
        if (resultingElse != null) {
          ifStatement.getParent().addAfter(ControlFlowUtils.stripBraces(resultingElse), ifStatement);
          resultingElse.delete();
        }
      }
    }

    private static PsiElement getReplacement(@NotNull PsiElement element,
                                             @NotNull PsiExpression expressionToReplace,
                                             @Nullable PsiExpression replacementExpression,
                                             @NotNull CommentTracker tracker) {
      Object marker = new Object();
      while (expressionToReplace.getParent() instanceof PsiParenthesizedExpression) {
        expressionToReplace = (PsiExpression)expressionToReplace.getParent();
      }
      PsiTreeUtil.mark(expressionToReplace, marker);
      PsiElement copy = element.copy();
      PsiExpression copyToReplace = (PsiExpression)PsiTreeUtil.releaseMark(copy, marker);
      assert copyToReplace != null;
      replacementExpression = PsiUtil.skipParenthesizedExprDown(replacementExpression);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      if (replacementExpression == null) {
        replacementExpression = factory.createExpressionFromText("()", null);
      } else if (MethodCallUtils.isNecessaryForSurroundingMethodCall(expressionToReplace, replacementExpression) ||
          isExplicitBoxingNecessary(expressionToReplace, replacementExpression)) {
        PsiType type = expressionToReplace.getType();
        if (type != null && !LambdaUtil.notInferredType(type)) {
          replacementExpression = factory
            .createExpressionFromText("(" + type.getCanonicalText() + ")" + tracker.text(replacementExpression), null);
        }
      }
      PsiTreeUtil.findChildrenOfType(copy, PsiComment.class).forEach(PsiElement::delete);
      PsiElement result = copyToReplace.replace(tracker.markUnchanged(replacementExpression));
      if (result instanceof PsiPolyadicExpression && result.getParent() instanceof PsiBinaryExpression) {
        // Convert parent binary expression to polyadic (like when replacing a+(x?b+c:..) with a+b+c)
        result.getParent().replace(factory.createExpressionFromText(result.getParent().getText(), result));
      }
      return copy == copyToReplace ? result : copy;
    }

    private static boolean isExplicitBoxingNecessary(PsiExpression expressionToReplace, PsiExpression replacementExpression) {
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expressionToReplace);
      return call != null && TypeConversionUtil.isPrimitiveAndNotNull(replacementExpression.getType());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalExpressionVisitor();
  }

  private class ConditionalExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression condition = expression.getCondition();
      PsiElement last = PsiTreeUtil.getDeepestLast(condition);
      if (last instanceof PsiWhiteSpace) {
        last = last.getPrevSibling();
      }
      if (last instanceof PsiErrorElement || expression.getThenExpression() == null) {
        // can't be fixed
        return;
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());

      if (parent instanceof PsiLocalVariable) {
        PsiTypeElement typeElement = ((PsiLocalVariable)parent).getTypeElement();
        if (typeElement.isInferredType() && !PsiTypesUtil.isDenotableType(typeElement.getType(), typeElement)) {
          return;
        }
      }

      boolean quickFixOnly = false;
      if (ignoreSimpleAssignmentsAndReturns) {
        if (parent instanceof PsiAssignmentExpression ||
            parent instanceof PsiReturnStatement ||
            parent instanceof PsiLocalVariable ||
            parent instanceof PsiLambdaExpression) {
          quickFixOnly = true;
        }
      }
      final boolean canSurround = !PsiType.NULL.equals(expression.getType()) && CodeBlockSurrounder.canSurround(expression);
      if (!canSurround && (ignoreExpressionContext || !isVisibleHighlight(expression))) {
        // quick fix is not built in this case (it will break code) and there will be no warning, so just return
        return;
      }
      if (quickFixOnly && !isOnTheFly()) return;
      registerError(expression, quickFixOnly ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    canSurround);
    }
  }
}