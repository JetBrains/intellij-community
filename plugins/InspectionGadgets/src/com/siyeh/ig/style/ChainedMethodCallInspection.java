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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ChainedMethodCallInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreFieldInitializations = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreThisSuperCalls = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("chained.method.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("chained.method.call.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("chained.method.call.ignore.option"), "m_ignoreFieldInitializations");
    panel.addCheckbox(InspectionGadgetsBundle.message("chained.method.call.ignore.this.super.option"), "m_ignoreThisSuperCalls");
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ChainedMethodCallVisitor();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false) {
      @Nullable
      @Override
      public PsiExpression getExpressionToExtract(PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
          return null;
        }
        final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
        return methodExpression.getQualifierExpression();
      }
    };
  }

  private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression reference = expression.getMethodExpression();
      final PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!isCallExpression(qualifier)) {
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      if (m_ignoreThisSuperCalls) {
        final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class);
        if (expressionList != null) {
          final PsiElement parent = expressionList.getParent();
          if (ExpressionUtils.isConstructorInvocation(parent)) {
            return;
          }
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isCallExpression(PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      return expression instanceof PsiMethodCallExpression || expression instanceof PsiNewExpression;
    }
  }
}