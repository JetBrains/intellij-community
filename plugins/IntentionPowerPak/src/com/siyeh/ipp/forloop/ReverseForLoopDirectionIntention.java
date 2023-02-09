// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.forloop;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.JavaTokenType.*;

/**
 * @author Bas Leijdekkers
 */
public class ReverseForLoopDirectionIntention extends Intention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("reverse.for.loop.direction.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("reverse.for.loop.direction.intention.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReverseForLoopDirectionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiForStatement forStatement = (PsiForStatement)element.getParent();
    final PsiDeclarationStatement initialization = (PsiDeclarationStatement)forStatement.getInitialization();
    if (initialization == null) {
      return;
    }
    final PsiBinaryExpression condition = (PsiBinaryExpression)PsiUtil.skipParenthesizedExprDown(forStatement.getCondition());
    if (condition == null) {
      return;
    }
    final PsiLocalVariable variable = (PsiLocalVariable)initialization.getDeclaredElements()[0];
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(condition.getLOperand());
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(condition.getROperand());
    if (lhs == null || rhs == null) {
      return;
    }
    final PsiExpressionStatement update = (PsiExpressionStatement)forStatement.getUpdate();
    if (update == null) {
      return;
    }
    CommentTracker ct = new CommentTracker();
    final PsiExpression updateExpression = update.getExpression();
    final String variableName = variable.getName();
    final StringBuilder newUpdateText = new StringBuilder();
    if (updateExpression instanceof PsiPrefixExpression prefixExpression) {
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (PLUSPLUS == tokenType) {
        newUpdateText.append("--");
      }
      else if (MINUSMINUS == tokenType) {
        newUpdateText.append("++");
      }
      else {
        return;
      }
      newUpdateText.append(variableName);
    }
    else if (updateExpression instanceof PsiPostfixExpression postfixExpression) {
      newUpdateText.append(variableName);
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (PLUSPLUS == tokenType) {
        newUpdateText.append("--");
      }
      else if (MINUSMINUS == tokenType) {
        newUpdateText.append("++");
      }
      else {
        return;
      }
    }
    else if (updateExpression instanceof PsiAssignmentExpression assignmentExpression) {
      newUpdateText.append(variableName);
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
      if (expression == null) {
        return;
      }
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (PLUSEQ == tokenType) {
        newUpdateText.append("-=").append(ct.text(expression));
      }
      else if (MINUSEQ == tokenType) {
        newUpdateText.append("+=").append(ct.text(expression));
      }
      else if (EQ == tokenType && expression instanceof PsiBinaryExpression binaryExpression) {
        final PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
        if (lOperand == null) {
          return;
        }
        final PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
        if (rOperand == null) {
          return;
        }
        final IElementType operationTokenType = binaryExpression.getOperationTokenType();
        newUpdateText.append('=');
        newUpdateText.append(ct.text(lOperand));
        if (PLUS == operationTokenType) {
          newUpdateText.append('-');
        }
        else if (MINUS == operationTokenType) {
          newUpdateText.append('+');
        }
        newUpdateText.append(ct.text(rOperand));
      }
      else {
        return;
      }
    }
    else {
      return;
    }
    final Project project = element.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final IElementType sign = condition.getOperationTokenType();
    final String negatedSign = ComparisonUtils.getNegatedComparison(sign);
    final StringBuilder conditionText = new StringBuilder();
    final StringBuilder newInitializerText = new StringBuilder();
    if (ExpressionUtils.isReferenceTo(lhs, variable)) {
      conditionText.append(variableName);
      conditionText.append(negatedSign);
      if (sign == GE) {
        conditionText.append(incrementExpression(initializer, true));
      }
      else if (sign == LE) {
        conditionText.append(incrementExpression(initializer, false));
      }
      else {
        conditionText.append(initializer.getText());
      }
      ct.markUnchanged(rhs);
      if (sign == LT) {
        newInitializerText.append(incrementExpression(rhs, false));
      }
      else if (sign == GT) {
        newInitializerText.append(incrementExpression(rhs, true));
      }
      else {
        newInitializerText.append(rhs.getText());
      }
    }
    else if (ExpressionUtils.isReferenceTo(rhs, variable)) {
      if (sign == LE) {
        conditionText.append(incrementExpression(initializer, true));
      }
      else if (sign == GE) {
        conditionText.append(incrementExpression(initializer, false));
      }
      else {
        conditionText.append(initializer.getText());
      }
      conditionText.append(negatedSign);
      conditionText.append(variableName);
      ct.markUnchanged(lhs);
      if (sign == GT) {
        newInitializerText.append(incrementExpression(lhs, false));
      }
      else if (sign == LT) {
        newInitializerText.append(incrementExpression(lhs, true));
      }
      else {
        newInitializerText.append(lhs.getText());
      }
    }
    else {
      return;
    }
    final PsiExpression newInitializer = factory.createExpressionFromText(newInitializerText.toString(), element);
    variable.setInitializer(newInitializer);
    ct.replace(condition, conditionText.toString());
    ct.replaceAndRestoreComments(updateExpression, newUpdateText.toString());
  }

  private static String incrementExpression(PsiExpression expression, boolean positive) {
    // TODO: properly support comment tracking
    return JavaPsiMathUtil.add(expression, positive ? 1 : -1, new CommentTracker());
  }
}