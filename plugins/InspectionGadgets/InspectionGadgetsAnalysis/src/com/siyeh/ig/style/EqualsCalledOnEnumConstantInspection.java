/*
 * Copyright 2008-2013 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class EqualsCalledOnEnumConstantInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.called.on.enum.constant.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.called.on.enum.constant.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return null;
    }
    return new EqualsCalledOnEnumValueFix();
  }

  private static class EqualsCalledOnEnumValueFix extends InspectionGadgetsFix {
     @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("equals.called.on.enum.constant.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent == null) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final StringBuilder newExpression = new StringBuilder();
      final PsiElement greatGrandParent = grandParent.getParent();
      final boolean not;
      final PsiPrefixExpression prefixExpression;
      if (greatGrandParent instanceof PsiPrefixExpression) {
        prefixExpression = (PsiPrefixExpression)greatGrandParent;
        final IElementType tokenType = prefixExpression.getOperationTokenType();
        not = JavaTokenType.EXCL == tokenType;
      }
      else {
        prefixExpression = null;
        not = false;
      }
      newExpression.append(qualifier.getText());
      if (not) {
        newExpression.append("!=");
      }
      else {
        newExpression.append("==");
      }
      if (arguments.length == 1) {
        newExpression.append(arguments[0].getText());
      }
      if (not) {
        PsiReplacementUtil.replaceExpression(prefixExpression, newExpression.toString());
      }
      else {
        PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString());
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsCalledOnEnumValueVisitor();
  }

  private static class EqualsCalledOnEnumValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isEqualsCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || !TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_ENUM)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiType comparedTypeErasure = TypeConversionUtil.erasure(qualifier.getType());
      final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(arguments[0].getType());
      if (comparedTypeErasure == null || comparisonTypeErasure == null ||
          !TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
