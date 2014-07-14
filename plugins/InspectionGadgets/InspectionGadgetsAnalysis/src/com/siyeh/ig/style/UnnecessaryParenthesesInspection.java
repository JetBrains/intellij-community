/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryParenthesesInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreClarifyingParentheses = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreParenthesesOnConditionals = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreParenthesesOnLambdaParameter = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.parentheses.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.parentheses.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("unnecessary.parentheses.option"), "ignoreClarifyingParentheses");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("unnecessary.parentheses.conditional.option"),
                             "ignoreParenthesesOnConditionals");
    optionsPanel.addCheckbox("Ignore parentheses around single no formal type lambda parameter", "ignoreParenthesesOnLambdaParameter");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryParenthesesVisitor();
  }

  private class UnnecessaryParenthesesFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.parentheses.remove.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiParameterList) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        final PsiParameterList parameterList = (PsiParameterList)element;
        final String text = parameterList.getParameters()[0].getName() + "->{}";
        final PsiLambdaExpression expression = (PsiLambdaExpression)factory.createExpressionFromText(text, element);
        element.replace(expression.getParameterList());
      } else {
        ParenthesesUtils.removeParentheses((PsiExpression)element, ignoreClarifyingParentheses);
      }
    }
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryParenthesesFix();
  }

  private class UnnecessaryParenthesesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitParameterList(PsiParameterList list) {
      super.visitParameterList(list);
      if (!ignoreParenthesesOnLambdaParameter && list.getParent() instanceof PsiLambdaExpression && list.getParametersCount() == 1) {
        final PsiParameter parameter = list.getParameters()[0];
        if (parameter.getTypeElement() == null && list.getFirstChild() != parameter && list.getLastChild() != parameter) {
          registerError(list);
        }
      }
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        return;
      }
      if (ignoreParenthesesOnConditionals && parent instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
        final PsiExpression condition = conditionalExpression.getCondition();
        if (expression == condition) {
          return;
        }
      }
      if (!ParenthesesUtils.areParenthesesNeeded(expression, ignoreClarifyingParentheses)) {
        registerError(expression);
        return;
      }
      super.visitParenthesizedExpression(expression);
    }
  }
}
