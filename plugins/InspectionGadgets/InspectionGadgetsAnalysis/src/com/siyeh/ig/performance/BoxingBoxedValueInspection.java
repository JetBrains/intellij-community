/*
 * Copyright 2011-2014 Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BoxingBoxedValueInspection extends BaseInspection {

  @NonNls
  static final Map<String, String> boxedPrimitiveMap =
    new HashMap<>(8);

  static {
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_LONG, "long");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "boxing.boxed.value.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "boxing.boxed.value.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new BoxingBoxedValueFix();
  }

  private static class BoxingBoxedValueFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "boxing.boxed.value.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiCallExpression parent = PsiTreeUtil.getParentOfType(
        element, PsiMethodCallExpression.class,
        PsiNewExpression.class);
      if (parent == null) {
        return;
      }
      parent.replace(element);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BoxingBoxedValueVisitor();
  }

  private static class BoxingBoxedValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType constructorType = expression.getType();
      if (constructorType == null) {
        return;
      }
      final String constructorTypeText =
        constructorType.getCanonicalText();
      if (!boxedPrimitiveMap.containsKey(constructorTypeText)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter parameter = parameters[0];
      final PsiType parameterType = parameter.getType();
      final String parameterTypeText = parameterType.getCanonicalText();
      final String boxableConstructorType =
        boxedPrimitiveMap.get(constructorTypeText);
      if (!boxableConstructorType.equals(parameterTypeText)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      final String argumentTypeText = argumentType.getCanonicalText();
      if (!constructorTypeText.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
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
      if (className == null) {
        return;
      }
      if (!boxedPrimitiveMap.containsKey(className)) {
        return;
      }
      if (method.getParameterList().getParametersCount() != 1) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      final String argumentTypeText = argumentType.getCanonicalText();
      if (!className.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }
  }
}
