/*
 * Copyright 2006-2015 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class SynchronizationUtil {

  private SynchronizationUtil() {}

  public static boolean isInSynchronizedContext(PsiElement element) {
    final PsiElement context =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiSynchronizedStatement.class, PsiClass.class, PsiLambdaExpression.class);
    if (context instanceof PsiSynchronizedStatement) {
      return true;
    }
    if (context instanceof PsiMethod) {
      final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)context;
      if (modifierListOwner.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return true;
      }
    }
    if (context instanceof PsiMethod || context instanceof PsiLambdaExpression) {
      final HoldsLockAssertionVisitor visitor = new HoldsLockAssertionVisitor();
      context.accept(visitor);
      final PsiAssertStatement assertStatement = visitor.getAssertStatement();
      return assertStatement != null && assertStatement.getTextOffset() + assertStatement.getTextLength() < element.getTextOffset();
    }
    return false;
  }

  public static boolean isCallToHoldsLock(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!"holdsLock".equals(name)) {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, "java.lang.Thread");
  }

  private static class HoldsLockAssertionVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiAssertStatement myAssertStatement = null;

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      if (myAssertStatement != null) return;
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (isCallToHoldsLock(condition)) {
        myAssertStatement = statement;
        stopWalking();
      }
    }

    public PsiAssertStatement getAssertStatement() {
      return myAssertStatement;
    }
  }
}