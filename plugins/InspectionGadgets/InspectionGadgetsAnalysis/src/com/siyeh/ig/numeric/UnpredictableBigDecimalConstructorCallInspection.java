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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnpredictableBigDecimalConstructorCallInspection
  extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreReferences = true;
  @SuppressWarnings("PublicField") public boolean ignoreComplexLiterals = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unpredictable.big.decimal.constructor.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unpredictable.big.decimal.constructor.call.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unpredictable.big.decimal.constructor.call.ignore.references.option"),
                             "ignoreReferences");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unpredictable.big.decimal.constructor.call.ignore.complex.literals.option"),
                             "ignoreComplexLiterals");
    return optionsPanel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiNewExpression newExpression = (PsiNewExpression)infos[0];
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return null;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) {
      return null;
    }
    final PsiExpression firstArgument = arguments[0];
    if (firstArgument instanceof PsiLiteralExpression) {
      final String text = firstArgument.getText();
      final char c = text.charAt(text.length() - 1);
      if (c != 'd' && c != 'D' && c != 'f' && c != 'F') {
        return new ReplaceDoubleArgumentWithStringFix("new BigDecimal(\"" + firstArgument.getText() + "\")");
      }
    }
    if (arguments.length == 1) {
      return new ReplaceDoubleArgumentWithStringFix("BigDecimal.valueOf(" + firstArgument.getText() + ')');
    }
    return null;
  }

  private static class ReplaceDoubleArgumentWithStringFix extends InspectionGadgetsFix {

    private final String argumentText;

    public ReplaceDoubleArgumentWithStringFix(String argumentText) {
      this.argumentText = argumentText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unpredictable.big.decimal.constructor.call.quickfix",
        argumentText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with 'BigDecimal.valueOf()'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiNewExpression newExpression = (PsiNewExpression)element.getParent();
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = arguments[0];
      if (firstArgument instanceof PsiLiteralExpression) {
        final String text = firstArgument.getText();
        final char c = text.charAt(text.length() - 1);
        if (c != 'd' && c != 'D' && c != 'f' && c != 'F') {
          PsiReplacementUtil.replaceExpression(firstArgument, '"' + firstArgument.getText() + '"');
          return;
        }
      }
      if (arguments.length == 1) {
        PsiReplacementUtil.replaceExpression(newExpression, "java.math.BigDecimal.valueOf(" + firstArgument.getText() + ')');
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnpredictableBigDecimalConstructorCallVisitor();
  }

  private class UnpredictableBigDecimalConstructorCallVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (classReference == null) {
        return;
      }
      final String name = classReference.getReferenceName();
      if (!"BigDecimal".equals(name)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass containingClass = constructor.getContainingClass();
      if (containingClass == null || !"java.math.BigDecimal".equals(containingClass.getQualifiedName())) {
        return;
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      final int length = parameterList.getParametersCount();
      if (length != 1 && length != 2) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter firstParameter = parameters[0];
      final PsiType type = firstParameter.getType();
      if (!PsiType.DOUBLE.equals(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      if (!checkExpression(firstArgument)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private boolean checkExpression(@Nullable PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiReferenceExpression) {
        if (ignoreReferences) {
          return false;
        }
      }
      else if (expression instanceof PsiPolyadicExpression) {
        if (ignoreComplexLiterals) {
          return false;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (!checkExpression(operand)) {
            return false;
          }
        }
      }
      return true;
    }
  }
}