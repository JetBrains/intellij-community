// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.initcause.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.initcause.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInitCauseVisitor();
  }

  private static class UnnecessaryInitCauseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"initCause".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression argument = ExpressionUtils.getOnlyExpressionInList(argumentList);
      if (!TypeUtils.expressionHasTypeOrSubtype(argument, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !CommonClassNames.JAVA_LANG_THROWABLE.equals(aClass.getQualifiedName())) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      final PsiNewExpression newExpression = findNewExpression(qualifier);
      if (!isCauseConstructorAvailable(newExpression, argument.getType()) || !canExpressionBeMovedBackwards(argument, newExpression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean canExpressionBeMovedBackwards(final PsiExpression cause, final PsiExpression newLocation) {
      if (cause == null || newLocation == null) return false;
      assert cause.getTextOffset() > newLocation.getTextOffset();
      final PsiCodeBlock block = PsiTreeUtil.getParentOfType(cause, PsiCodeBlock.class);
      final PsiCodeBlock newBlock = PsiTreeUtil.getParentOfType(newLocation, PsiCodeBlock.class);
      if (newBlock == null || !PsiTreeUtil.isAncestor(block, newBlock, false)) return false;
      final int offset = newLocation.getTextOffset();
      final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
      cause.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (!result.get().booleanValue()) {
            return;
          }
          super.visitReferenceExpression(expression);
          final PsiElement target = expression.resolve();
          if (!(target instanceof PsiVariable)) {
            return;
          }
          final PsiElement[] defs = DefUseUtil.getDefs(block, (PsiVariable)target, cause);
          for (PsiElement def : defs) {
            if (def.getTextOffset() > offset) {
              result.set(Boolean.FALSE);
            }
          }
        }
      });
      return result.get().booleanValue();
    }

    public static boolean isCauseConstructorAvailable(PsiNewExpression newExpression, PsiType causeType) {
      if (newExpression == null || causeType == null) {
        return false;
      }
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) {
        return false;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      outer:
      for (PsiMethod constructor1 : aClass.getConstructors()) {
        final PsiParameterList parameterList = constructor1.getParameterList();
        if (parameterList.getParametersCount() == arguments.length + 1) {
          final PsiParameter[] parameters = parameterList.getParameters();
          for (int i = 0; i < arguments.length; i++) {
            final PsiExpression argument = arguments[i];
            final PsiParameter parameter = parameters[i];
            final PsiType type = argument.getType();
            if (type == null || !parameter.getType().isAssignableFrom(type)) {
              continue outer;
            }
          }
          final PsiParameter lastParameter = parameters[parameters.length - 1];
          if (!lastParameter.getType().isAssignableFrom(causeType) || !PsiUtil.isAccessible(constructor1, newExpression, null)) {
            continue;
          }
          return true;
        }
      }
      return false;
    }
  }

  @Nullable
  static PsiNewExpression findNewExpression(PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      return (PsiNewExpression)expression;
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, null);
      if (!(definition instanceof PsiNewExpression)) {
        return null;
      }
      return (PsiNewExpression) definition;
    }
    return null;
  }
}
