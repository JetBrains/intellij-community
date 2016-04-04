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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public abstract class MisorderedAssertEqualsArgumentsInspectionBase extends BaseInspection {

  @NonNls
  private static final Set<String> methodNames =
    ContainerUtil.newHashSet("assertEquals", "assertEqualsNoOrder", "assertNotEquals", "assertArrayEquals", "assertSame",
                             "assertNotSame", "failNotSame", "failNotEquals");

  public abstract boolean checkTestNG();

  @Override
  @NotNull
  public final String getDisplayName() {
    return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.display.name");
  }

  @Override
  @NotNull
  protected final String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.problem.descriptor");
  }

  @Override
  public final InspectionGadgetsFix buildFix(Object... infos) {
    return new FlipArgumentsFix();
  }

  private static class FlipArgumentsFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.flip.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (callExpression == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final PsiMethod method = (PsiMethod)methodExpression.resolve();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final boolean junit;
      if (InheritanceUtil.isInheritor(containingClass, "org.testng.Assert")) {
        junit = false;
      }
      else if (InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit") ||
               InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") ||
               InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
        junit = true;
      }
      else {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType stringType = TypeUtils.getStringType(callExpression);
      final PsiType parameterType1 = parameters[0].getType();
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression expectedArgument;
      final PsiExpression actualArgument;
      if (junit) {
        if (parameterType1.equals(stringType) && parameters.length > 2) {
          expectedArgument = arguments[1];
          actualArgument = arguments[2];
        }
        else {
          expectedArgument = arguments[0];
          actualArgument = arguments[1];
        }
      }
      else {
        actualArgument = arguments[0];
        expectedArgument = arguments[1];
      }
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new MisorderedAssertEqualsParametersVisitor();
  }

  private class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!methodNames.contains(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 2) {
        return;
      }
      final PsiType stringType = TypeUtils.getStringType(expression);
      final PsiClass containingClass = method.getContainingClass();
      final PsiExpression expectedArgument;
      final PsiExpression actualArgument;
      if (checkTestNG() ?
          InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit") :
          InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") ||
          InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
        final PsiType firstArgumentType = arguments[0].getType();
        if (stringType.equals(firstArgumentType) && arguments.length > 2) {
          expectedArgument = arguments[1];
          actualArgument = arguments[2];
        }
        else {
          expectedArgument = arguments[0];
          actualArgument = arguments[1];
        }
      } else if (checkTestNG() && InheritanceUtil.isInheritor(containingClass, "org.testng.Assert")){
        expectedArgument = arguments[1];
        actualArgument = arguments[0];
      } else {
        return;
      }
      if (expectedArgument == null || actualArgument == null) {
        return;
      }
      if (looksLikeExpectedArgument(expectedArgument) || !looksLikeExpectedArgument(actualArgument)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private boolean looksLikeExpectedArgument(@Nullable PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (ExpressionUtils.computeConstantExpression(expression) != null || PsiType.NULL.equals(type)) {
        return true;
      }
      if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiEnumConstant) {
          return true;
        }
        else if ((target instanceof PsiField)) {
          final PsiField field = (PsiField)target;
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
            return true;
          }
        }
        else if (target instanceof PsiLocalVariable) {
          final PsiVariable variable = (PsiLocalVariable)target;
          final PsiExpression definition = VariableSearchUtils.findDefinition(referenceExpression, variable);
          if (LibraryUtil.isOnlyLibraryCodeUsed(definition)) {
            return true;
          }
        }
      }
      if (expression instanceof PsiCallExpression && type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiCompiledElement) {
            return LibraryUtil.isOnlyLibraryCodeUsed(expression);
        }
      }
      return false;
    }
  }
}
