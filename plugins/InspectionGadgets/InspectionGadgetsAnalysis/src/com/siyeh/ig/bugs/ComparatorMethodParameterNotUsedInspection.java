/*
 * Copyright 2007-2016 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ComparatorMethodParameterNotUsedInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "comparator.method.parameter.not.used.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "comparator.method.parameter.not.used.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CompareMethodDoesNotUseParameterVisitor();
  }

  private static class CompareMethodDoesNotUseParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!MethodUtils.isComparatorCompare(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      checkParameterList(method.getParameterList(), method);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      super.visitLambdaExpression(expression);
      final PsiClass functionalInterface = PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType());
      if (functionalInterface == null || !CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface.getQualifiedName()) ||
          ControlFlowUtils.lambdaExpressionAlwaysThrowsException(expression)) {
        return;
      }
      checkParameterList(expression.getParameterList(), expression);
    }

    private void checkParameterList(PsiParameterList parameterList, PsiElement context) {
      final ParameterAccessVisitor visitor = new ParameterAccessVisitor(parameterList.getParameters());
      context.accept(visitor);
      for (PsiParameter unusedParameter : visitor.getUnusedParameters()) {
        registerVariableError(unusedParameter);
      }
    }

    private static class ParameterAccessVisitor extends JavaRecursiveElementWalkingVisitor {

      private final Set<PsiParameter> parameters;

      private ParameterAccessVisitor(@NotNull PsiParameter[] parameters) {
        this.parameters = new HashSet<>(Arrays.asList(parameters));
      }

      @Override
      public void visitReferenceExpression(
        PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (parameters.isEmpty()) {
          return;
        }
        if (expression.getQualifierExpression() != null) {
          // optimization
          // references to parameters are never qualified
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiParameter)) {
          return;
        }
        final PsiParameter parameter = (PsiParameter)target;
        parameters.remove(parameter);
      }

      private Collection<PsiParameter> getUnusedParameters() {
        return Collections.unmodifiableSet(parameters);
      }
    }
  }
}