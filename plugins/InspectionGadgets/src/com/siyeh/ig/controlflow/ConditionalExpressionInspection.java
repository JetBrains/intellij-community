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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConditionalExpressionInspection extends BaseInspection {

  private static final Logger LOG = Logger.getInstance(ConditionalExpressionInspection.class);

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSimpleAssignmentsAndReturns = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreExpressionContext = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "conditional.expression.display.name");
  }

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
      if (!PsiTreeUtil.processElements(expression, e -> !(e instanceof PsiErrorElement))) {
        return;
      }
      final PsiElement expressionParent = expression.getParent();
      if (expressionParent instanceof PsiLambdaExpression) {
        final PsiCodeBlock codeBlock = RefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)expressionParent);
        final PsiStatement statement = codeBlock.getStatements()[0];
        if (statement instanceof PsiReturnStatement) {
          final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
          expression = (PsiConditionalExpression)returnStatement.getReturnValue();
        }
        else {
          final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
          expression = (PsiConditionalExpression)expressionStatement.getExpression();
        }
      }
      final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final PsiVariable variable =
        statement instanceof PsiDeclarationStatement ? PsiTreeUtil.getParentOfType(expression, PsiVariable.class) : null;
      PsiExpression thenExpression = ParenthesesUtils.stripParentheses(expression.getThenExpression());
      PsiExpression elseExpression = ParenthesesUtils.stripParentheses(expression.getElseExpression());
      final PsiExpression condition = ParenthesesUtils.stripParentheses(expression.getCondition());
      CommentTracker tracker = new CommentTracker();
      final StringBuilder newStatement = new StringBuilder();
      newStatement.append("if(");
      if (condition != null) {
        newStatement.append(tracker.text(condition));
      }
      newStatement.append(')');
      if (variable != null) {
        final String name = variable.getName();
        newStatement.append(name).append('=');
        PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
          return;
        }
        if (initializer instanceof PsiArrayInitializerExpression) {
          final int conditionIdx = ArrayUtilRt.find(((PsiArrayInitializerExpression)initializer).getInitializers(), expression);
          if (conditionIdx >= 0) {
            initializer = (PsiExpression)initializer.replace(RefactoringUtil.convertInitializerToNormalExpression(initializer, variable.getType()));
            final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
            LOG.assertTrue(arrayInitializer != null, initializer.getText());
            expression = (PsiConditionalExpression)arrayInitializer.getInitializers()[conditionIdx];
            thenExpression = expression.getThenExpression();
            elseExpression = expression.getElseExpression();
          }
        }
        appendElementTextWithoutParentheses(initializer, expression, thenExpression, newStatement, tracker);
        newStatement.append("; else ").append(name).append('=');
        appendElementTextWithoutParentheses(initializer, expression, elseExpression, newStatement, tracker);
        newStatement.append(';');
        tracker.delete(initializer);
        final PsiManager manager = statement.getManager();
        final PsiStatement ifStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(newStatement.toString(), statement);
        final PsiElement parent = statement.getParent();
        final PsiElement addedElement = parent.addAfter(ifStatement, statement);
        tracker.insertCommentsBefore(addedElement);
        final CodeStyleManager styleManager = CodeStyleManager.getInstance(manager.getProject());
        styleManager.reformat(addedElement);
      }
      else {
        final boolean addBraces = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class, true, PsiStatement.class) != null;
        if (addBraces || thenExpression == null) {
          newStatement.append('{');
        }
        appendElementTextWithoutParentheses(statement, expression, thenExpression, newStatement, tracker);
        if (addBraces) {
          newStatement.append("} else {");
        }
        else {
          if (thenExpression == null) {
            newStatement.append('}');
          }
          newStatement.append(" else ");
          if (elseExpression == null) {
            newStatement.append('{');
          }
        }
        appendElementTextWithoutParentheses(statement, expression, elseExpression, newStatement, tracker);
        if (addBraces || elseExpression == null) {
          newStatement.append('}');
        }
        PsiReplacementUtil.replaceStatement(statement, newStatement.toString(), tracker);
      }
    }

    private static void appendElementTextWithoutParentheses(@NotNull PsiElement element,
                                                            @NotNull PsiExpression expressionToReplace,
                                                            @Nullable PsiExpression replacementExpression,
                                                            @NotNull StringBuilder out,
                                                            CommentTracker tracker) {
      final PsiElement expressionParent = expressionToReplace.getParent();
      if (expressionParent instanceof PsiParenthesizedExpression) {
        final PsiElement grandParent = expressionParent.getParent();
        if (replacementExpression == null || !(grandParent instanceof PsiExpression) ||
            !ParenthesesUtils.areParenthesesNeeded(replacementExpression, (PsiExpression) grandParent, false)) {
          appendElementTextWithoutParentheses(element, (PsiExpression)expressionParent, replacementExpression, out, tracker);
          return;
        }
      }
      final boolean needsCast =
        replacementExpression != null && MethodCallUtils.isNecessaryForSurroundingMethodCall(expressionToReplace, replacementExpression);
      appendElementText(element, expressionToReplace, replacementExpression, needsCast, out, tracker);
    }

    private static void appendElementText(@NotNull PsiElement element,
                                          @NotNull PsiExpression elementToReplace,
                                          @Nullable PsiExpression replacementExpression,
                                          boolean insertCast,
                                          @NotNull StringBuilder out,
                                          CommentTracker tracker) {
      if (element.equals(elementToReplace)) {
        final String replacementText = (replacementExpression == null) ? "" : replacementExpression.getText();
        final PsiType type = GenericsUtil.getVariableTypeByExpressionType(ExpectedTypeUtils.findExpectedType(elementToReplace, true));
        if (insertCast && type != null) {
          out.append('(').append(type.getCanonicalText()).append(')');
        }
        out.append(replacementText);
        return;
      }
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        if (!(element instanceof PsiComment)) {
          out.append(tracker.text(element));
        }
      }
      for (PsiElement child : children) {
        appendElementText(child, elementToReplace, replacementExpression, insertCast, out, tracker);
      }
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
      if (last instanceof PsiErrorElement) {
        // can't be fixed
        return;
      }
      if (ignoreSimpleAssignmentsAndReturns) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiParenthesizedExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiAssignmentExpression ||
            parent instanceof PsiReturnStatement ||
            parent instanceof PsiLocalVariable ||
            parent instanceof PsiLambdaExpression) {
          return;
        }
        if (!isOnTheFly()) return;
        registerError(expression, ProblemHighlightType.INFORMATION, Boolean.TRUE);
      }
      else {
        final boolean expressionContext = isExpressionContext(expression);
        if (expressionContext && (ignoreExpressionContext || isVisibleHighlight(expression))) {
          return;
        }
        registerError(expression, Boolean.valueOf(!expressionContext));
      }
    }

    private boolean isExpressionContext(PsiConditionalExpression expression) {
      final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
      if (member instanceof PsiField) {
        return true;
      }
      if (!(member instanceof PsiMethod)) {
        return false;
      }
      final PsiMethod method = (PsiMethod)member;
      if (!method.isConstructor()) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true,
                                    PsiLambdaExpression.class, PsiStatement.class, PsiMember.class);
      return ExpressionUtils.isConstructorInvocation(methodCallExpression);
    }
  }
}