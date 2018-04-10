/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
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
    final CommentTracker commentTracker = new CommentTracker();
    if (annotation == null) {
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
      commentTracker.delete(statementToDelete);
    }
    PsiReplacementUtil.replaceExpressionAndShorten(referenceExpression,
                                                   "java.util.Objects.requireNonNull(" + commentTracker.text(referenceExpression) + ")",
                                                   commentTracker);
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
      if (annotation != null && !AnnotationUtil.isExternalAnnotation(annotation) && !AnnotationUtil.isInferredAnnotation(annotation)) {
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

    static boolean isIfStatementNullCheck(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiIfStatement)) {
        return false;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        return false;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (!isSimpleThrowStatement(thenBranch)) {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      return ComparisonUtils.isNullComparison(condition, variable, true);
    }

    static boolean isNotNullAssertion(PsiStatement statement, @NotNull PsiVariable variable) {
      if (!(statement instanceof PsiAssertStatement)) {
        return false;
      }
      final PsiAssertStatement assertStatement = (PsiAssertStatement)statement;
      final PsiExpression condition = assertStatement.getAssertCondition();
      return ComparisonUtils.isNullComparison(condition, variable, false);
    }

    public static boolean isSimpleThrowStatement(PsiStatement element) {
      if (element instanceof PsiThrowStatement) {
        return true;
      }
      else if (element instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length != 1) {
          return false;
        }
        final PsiStatement statement = statements[0];
        return isSimpleThrowStatement(statement);
      }
      return false;
    }
  }
}
