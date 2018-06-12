/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class WaitNotifyNotInSynchronizedContextInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("wait.notify.not.in.synchronized.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String text = (String)infos[0];
    return InspectionGadgetsBundle.message("wait.notify.while.not.synchronized.on.problem.descriptor", text);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaiNotifyNotInSynchronizedContextVisitor();
  }

  private static class WaiNotifyNotInSynchronizedContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isNotifyOrNotifyAllCall(expression) &&
          !ThreadingUtils.isWaitCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        if (isSynchronizedOnThis(expression) || isCoveredByGuardedByAnnotation(expression, "this")) {
          return;
        }
        registerError(expression, PsiKeyword.THIS);
      }
      else if (qualifier instanceof PsiReferenceExpression) {
        if (isSynchronizedOn(expression, qualifier)) {
          return;
        }
        final String text = qualifier.getText();
        if (isCoveredByGuardedByAnnotation(expression, text)) {
          return;
        }
        registerError(expression, text);
      }
    }

    private static boolean isCoveredByGuardedByAnnotation(PsiElement context, String guard) {
      final PsiMember member = PsiTreeUtil.getParentOfType(context, PsiMember.class);
      if (member == null) {
        return false;
      }
      return guard.equals(JCiPUtil.findGuardForMember(member));
    }

    private static boolean isSynchronizedOn(@NotNull PsiElement element, @NotNull PsiExpression target) {
      final PsiSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(element, PsiSynchronizedStatement.class);
      if (synchronizedStatement == null) {
        return false;
      }
      final PsiExpression lockExpression = ParenthesesUtils.stripParentheses(synchronizedStatement.getLockExpression());
      final EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
      return checker.expressionsAreEquivalent(lockExpression, target) || isSynchronizedOn(synchronizedStatement, target);
    }

    private static boolean isSynchronizedOnThis(@NotNull PsiElement element) {
      final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiSynchronizedStatement.class);
      if (context instanceof PsiSynchronizedStatement) {
        final PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)context;
        final PsiExpression lockExpression = ParenthesesUtils.stripParentheses(synchronizedStatement.getLockExpression());
        return lockExpression instanceof PsiThisExpression || isSynchronizedOnThis(synchronizedStatement);
      }
      else if (context instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)context;
        if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return true;
        }
      }
      return false;
    }
  }
}
