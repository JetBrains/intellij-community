/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.VisibilityUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryConstructorInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAnnotations = false;

  @Override
  @NotNull
  public String getID() {
    return "RedundantNoArgConstructor";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.constructor.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "unnecessary.constructor.annotation.option"),
      this, "ignoreAnnotations");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryConstructorFix();
  }

  private static class UnnecessaryConstructorFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constructor.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement nameIdentifier = descriptor.getPsiElement();
      final PsiElement constructor = nameIdentifier.getParent();
      assert constructor != null;
      deleteElement(constructor);
    }
  }

  private class UnnecessaryConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 1) {
        return;
      }
      final PsiMethod constructor = constructors[0];
      if (!constructor.isPhysical() || constructor.getNameIdentifier() == null) {
        return;
      }
      if (!aClass.isEnum()) {
        String modifier = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
        if (!constructor.hasModifierProperty(modifier)) {
          return;
        }
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      if (ignoreAnnotations) {
        final PsiModifierList modifierList = constructor.getModifierList();
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        if (annotations.length > 0) {
          return;
        }
      }
      final PsiReferenceList throwsList = constructor.getThrowsList();
      final PsiJavaCodeReferenceElement[] elements = throwsList.getReferenceElements();
      if (elements.length != 0) {
        return;
      }
      final PsiCodeBlock body = constructor.getBody();
      if (ControlFlowUtils.isEmptyCodeBlock(body) ||
          isSuperConstructorInvocationWithoutArguments(ControlFlowUtils.getOnlyStatementInBlock(body))) {
        registerMethodError(constructor);
      }
    }

    private boolean isSuperConstructorInvocationWithoutArguments(PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList.getExpressions().length != 0) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      return PsiKeyword.SUPER.equals(methodExpression.getReferenceName());
    }
  }
}