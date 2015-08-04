/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ipp.asserttoif;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ObjectsRequireNonNullIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new NullCheckedAssignmentPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return;
    }
    final PsiVariable variable = (PsiVariable)target;
    final List<String> notNulls = NullableNotNullManager.getInstance(element.getProject()).getNotNulls();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(variable, notNulls);
    if (annotation != null) {
      annotation.delete();
    } else {
      final PsiStatement referenceStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiStatement.class);
      if (referenceStatement == null) {
        return;
      }
      final PsiElement parent = referenceStatement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiCodeBlock codeBlock = (PsiCodeBlock)parent;
      final PsiStatement[] statements = codeBlock.getStatements();
      PsiStatement statementToDelete = null;
      for (PsiStatement statement : statements) {
        if (statement == referenceStatement) {
          break;
        }
        if (NullCheckedAssignmentPredicate.isNotNullAssertion(statement, variable) ||
            NullCheckedAssignmentPredicate.isIfStatementNullCheck(statement, variable)) {
          statementToDelete = statement;
          break;
        }
      }
      if (statementToDelete == null) {
        return;
      }
      statementToDelete.delete();
    }
    PsiReplacementUtil
      .replaceExpressionAndShorten(referenceExpression, "java.util.Objects.requireNonNull(" + referenceExpression.getText() + ")");
  }

  private static class NullCheckedAssignmentPredicate implements PsiElementPredicate {

    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (!PsiUtil.isLanguageLevel7OrHigher(element)) {
        return false;
      }
      if (!(element instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      if (PsiUtil.isAccessedForWriting(referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)target;
      if (ClassUtils.findClass("java.util.Objects", element) == null) {
        return false;
      }
      final PsiAnnotation annotation = NullableNotNullManager.getInstance(variable.getProject()).getNotNullAnnotation(variable, true);
      if (annotation != null && annotation.isWritable()) {
        return true;
      }
      final PsiStatement referenceStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiStatement.class);
      final PsiElement parent = referenceStatement != null ? referenceStatement.getParent() : null;
      if (!(parent instanceof PsiCodeBlock)) {
        return false;
      }
      final PsiCodeBlock codeBlock = (PsiCodeBlock)parent;
      final PsiStatement[] statements = codeBlock.getStatements();
      for (PsiStatement statement : statements) {
        if (statement == referenceStatement) {
          return false;
        }
        if (isNotNullAssertion(statement, variable) || isIfStatementNullCheck(statement, variable)) {
          return true;
        }
      }
      return false;
    }

    private static boolean isIfStatementNullCheck(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiIfStatement)) {
        return false;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        return false;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (!IfStatementPredicate.isSimpleThrowStatement(thenBranch)) {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      return isNullComparison(condition, variable, true);
    }

    private static boolean isNotNullAssertion(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiAssertStatement)) {
        return false;
      }
      final PsiAssertStatement assertStatement = (PsiAssertStatement)statement;
      final PsiExpression condition = assertStatement.getAssertCondition();
      return isNullComparison(condition, variable, false);
    }

    private static boolean isNullComparison(PsiExpression expression, @NotNull PsiVariable variable, boolean equal) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (equal) {
        if (!JavaTokenType.EQEQ.equals(tokenType)) {
          return false;
        }
      }
      else {
        if (!JavaTokenType.NE.equals(tokenType)) {
          return false;
        }
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      return PsiType.NULL.equals(rhs.getType()) && VariableAccessUtils.evaluatesToVariable(lhs, variable) ||
             PsiType.NULL.equals(lhs.getType()) && VariableAccessUtils.evaluatesToVariable(rhs, variable);
    }
  }
}
