/*
 * Copyright 2008-2016 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SynchronizationOnLocalVariableOrMethodParameterInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportLocalVariables = true;
  @SuppressWarnings("PublicField")
  public boolean reportMethodParameters = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Boolean localVariable = (Boolean)infos[0];
    if (localVariable.booleanValue()) {
      return InspectionGadgetsBundle.message("synchronization.on.local.variable.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("synchronization.on.method.parameter.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizationOnLocalVariableVisitor();
  }

  private class SynchronizationOnLocalVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      if (!reportLocalVariables && !reportMethodParameters) {
        return;
      }
      final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(statement.getLockExpression());
      if (!(lockExpression instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      if (referenceExpression.isQualified()) {
        return;
      }
      boolean localVariable = false;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiLocalVariable variable) {
        if (!reportLocalVariables || isSynchronizedCollection(variable, referenceExpression) || isReferencedToField(variable, referenceExpression)) {
          return;
        }
        localVariable = true;
      }
      else if (target instanceof PsiParameter parameter) {
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          if (!reportMethodParameters) {
            return;
          }
        }
        else {
          if (!reportLocalVariables) {
            return;
          }
          localVariable = true;
        }
      }
      else {
        return;
      }
      final PsiElement statementScope = getScope(statement);
      final PsiElement targetScope = getScope(target);
      if (statementScope != targetScope || isEscaping((PsiVariable)target)) {
        return;
      }
      registerError(referenceExpression, Boolean.valueOf(localVariable));
    }

    private static boolean isReferencedToField(PsiLocalVariable variable, PsiReferenceExpression referenceExpression) {
      PsiElement parent = PsiTreeUtil.findCommonParent(variable, referenceExpression);
      if (parent == null) {
        return false;
      }
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && !isField(initializer)) {
        return false;
      }
      return !VariableAccessUtils.variableIsAssigned(variable, t -> isField(t), parent);
    }

    private static boolean isField(PsiExpression expression) {
      if (expression instanceof PsiAssignmentExpression assignmentExpression) {
        return isField(assignmentExpression.getRExpression());
      }
      if (expression instanceof PsiReferenceExpression referenceExpression) {
        return referenceExpression.resolve() instanceof PsiField;
      }
      return false;
    }

    private static PsiElement getScope(PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class, PsiClassInitializer.class);
    }

    private static boolean isSynchronizedCollection(@NotNull PsiVariable variable, PsiReferenceExpression referenceExpression) {
      final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
      if (!(definition instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      @NonNls final String methodName = method.getName();
      if (!methodName.startsWith("synchronized")) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      return containingClass != null && "java.util.Collections".equals(containingClass.getQualifiedName());
    }
  }

  private static boolean isEscaping(PsiVariable variable) {
    final PsiElement scope;
    if (variable instanceof PsiParameter parameter) {
      scope = parameter.getDeclarationScope();
    }
    else if (variable instanceof PsiLocalVariable) {
      scope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    }
    else {
      throw new AssertionError();
    }
    if (scope == null) {
      // incomplete code
      return true;
    }
    final EscapeVisitor visitor = new EscapeVisitor(variable, scope);
    scope.accept(visitor);
    return visitor.isEscaping();
  }

  private static class EscapeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiVariable myVariable;
    private final PsiElement myContext;
    private boolean escaping = false;

    EscapeVisitor(@NotNull PsiVariable variable, @NotNull PsiElement context) {
      myVariable = variable;
      myContext = context;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (escaping) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (!myVariable.equals(target)) {
        return;
      }
      final PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiMember.class, PsiLambdaExpression.class);
      if (context != null && PsiTreeUtil.isAncestor(myContext, context, true)) {
        // strictly speaking a value can also escape via method call or return statement, but
        // since it is difficult to guarantee synchronization and thus correctness on accessing such values,
        // we want to warn on those cases and don't detect them here.
        escaping = true;
      }
    }

    public boolean isEscaping() {
      return escaping;
    }
  }
}