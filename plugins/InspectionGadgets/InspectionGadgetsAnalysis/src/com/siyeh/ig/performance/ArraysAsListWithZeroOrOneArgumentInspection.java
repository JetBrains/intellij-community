/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ArraysAsListWithZeroOrOneArgumentInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("arrays.as.list.with.zero.or.one.argument.display.name");
  }

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

  private static class ArraysAsListWithOneArgumentFix extends InspectionGadgetsFix {

    private final boolean myEmpty;

    private ArraysAsListWithOneArgumentFix(boolean isEmpty) {
      myEmpty = isEmpty;
    }

    @NotNull
    @Override
    public String getName() {
      if (myEmpty) {
        return InspectionGadgetsBundle.message("arrays.as.list.with.zero.arguments.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("arrays.as.list.with.one.argument.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      if (myEmpty) {
        PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Collections.emptyList()");
      }
      else {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
        final String parameterText = parameterList != null ? parameterList.getText() : "";
        PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Collections." + parameterText +
                                                                             "singletonList" + argumentList.getText());
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
      if (arguments.length == 1) {
        final PsiExpression argument = arguments[0];
        final PsiType type = argument.getType();
        if (type instanceof PsiArrayType) {
          return;
        }
      }
      else if (arguments.length != 0) {
        return;
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
      registerMethodCallError(expression, Boolean.valueOf(arguments.length == 0));
    }
  }
}
