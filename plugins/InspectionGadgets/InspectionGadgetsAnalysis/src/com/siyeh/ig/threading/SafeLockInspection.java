/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SafeLockInspection extends BaseInspection { // todo extend ResourceInspection?

  @Override
  @NotNull
  public String getID() {
    return "LockAcquiredButNotSafelyReleased";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("safe.lock.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "safe.lock.problem.descriptor", text);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SafeLockVisitor();
  }

  private static class SafeLockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isLockAcquireMethod(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      final PsiVariable boundVariable;
      final PsiReferenceExpression referenceExpression;
      final LockType type;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        referenceExpression = (PsiReferenceExpression)qualifierExpression;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        boundVariable = (PsiVariable)target;
        type = LockType.REGULAR;
      }
      else if (qualifierExpression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)qualifierExpression;
        final PsiReferenceExpression methodExpression1 =
          methodCallExpression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression1.getReferenceName();
        if ("readLock".equals(methodName)) {
          type = LockType.READ;
        }
        else if ("writeLock".equals(methodName)) {
          type = LockType.WRITE;
        }
        else {
          return;
        }
        final PsiExpression qualifierExpression1 =
          methodExpression1.getQualifierExpression();
        if (!(qualifierExpression1 instanceof PsiReferenceExpression)) {
          return;
        }
        referenceExpression =
          (PsiReferenceExpression)qualifierExpression1;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        boundVariable = (PsiVariable)target;
      }
      else {
        return;
      }
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      while (nextStatement != null && !isSignificant(nextStatement)) {
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      if (!(nextStatement instanceof PsiTryStatement)) {
        registerError(expression, referenceExpression);
        return;
      }
      final PsiTryStatement tryStatement =
        (PsiTryStatement)nextStatement;
      if (lockIsUnlockedInFinally(tryStatement, boundVariable, type)) {
        return;
      }
      registerError(expression, referenceExpression);
    }

    private static boolean isSignificant(@NotNull PsiStatement statement) {
      final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
      statement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitExpression(PsiExpression expression) {
          super.visitExpression(expression);
          result.set(Boolean.FALSE);
          stopWalking();
        }
      });
      return !result.get().booleanValue();
    }

    private static boolean lockIsUnlockedInFinally(
      PsiTryStatement tryStatement, PsiVariable boundVariable,
      LockType type) {
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) {
        return false;
      }
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      final UnlockVisitor visitor =
        new UnlockVisitor(boundVariable, type);
      finallyBlock.accept(visitor);
      return visitor.containsUnlock();
    }

    private static boolean isLockAcquireMethod(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"lock".equals(methodName) &&
          !"lockInterruptibly".equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                  "java.util.concurrent.locks.Lock");
    }
  }

  private static class UnlockVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean containsUnlock;
    private final PsiVariable variable;
    private final LockType type;

    private UnlockVisitor(@NotNull PsiVariable variable,
                          @NotNull LockType type) {
      this.variable = variable;
      this.type = type;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!containsUnlock) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression call) {
      if (containsUnlock) {
        return;
      }
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"unlock".equals(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier instanceof PsiReferenceExpression) {
        if (type != LockType.REGULAR) {
          return;
        }
        final PsiReference reference = (PsiReference)qualifier;
        final PsiElement target = reference.resolve();
        if (variable.equals(target)) {
          containsUnlock = true;
        }
      }
      else if (qualifier instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)qualifier;
        final PsiReferenceExpression methodExpression1 =
          methodCallExpression.getMethodExpression();
        @NonNls final String methodName1 =
          methodExpression1.getReferenceName();
        if (type == LockType.READ && "readLock".equals(methodName1) ||
            type == LockType.WRITE &&
            "writeLock".equals(methodName1)) {
          final PsiExpression qualifierExpression =
            methodExpression1.getQualifierExpression();
          if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return;
          }
          final PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression)qualifierExpression;
          final PsiElement target = referenceExpression.resolve();
          if (variable.equals(target)) {
            containsUnlock = true;
          }
        }
      }
    }

    boolean containsUnlock() {
      return containsUnlock;
    }
  }

  private enum LockType {
    READ, WRITE, REGULAR
  }
}
