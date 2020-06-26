// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ArraysAsListWithZeroOrOneArgumentInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean isEmpty = (Boolean)infos[0];
    if (isEmpty.booleanValue()) {
      return InspectionGadgetsBundle.message("arrays.as.list.with.zero.arguments.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("arrays.as.list.with.one.argument.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean isEmpty = (Boolean)infos[0];
    return new ArraysAsListWithOneArgumentFix(isEmpty.booleanValue());
  }

  private static final class ArraysAsListWithOneArgumentFix extends InspectionGadgetsFix {

    private final boolean myEmpty;

    private ArraysAsListWithOneArgumentFix(boolean isEmpty) {
      myEmpty = isEmpty;
    }

    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myEmpty ? "Collections.emptyList()" : "Collections.singletonList()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
      CommentTracker commentTracker = new CommentTracker();
      final String parameterText = parameterList != null ? commentTracker.text(parameterList) : "";
      if (myEmpty) {
        PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Collections." + parameterText +
                                                                             "emptyList()", commentTracker);
      }
      else {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Collections." + parameterText +
                                                                             "singletonList" + commentTracker.text(argumentList), commentTracker);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayAsListWithOneArgumentVisitor();
  }

  private static class ArrayAsListWithOneArgumentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!"asList".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) return;

      boolean empty = false;
      if (arguments.length == 0) {
        empty = true;
      }
      else {
        final PsiExpression argument = arguments[0];
        if (!MethodCallUtils.isVarArgCall(expression)) {
          if (!ConstructionUtils.isEmptyArrayInitializer(argument)) {
            return;
          }
          empty = true;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.util.Arrays".equals(className)) {
        return;
      }
      registerMethodCallError(expression, empty);
    }
  }
}
