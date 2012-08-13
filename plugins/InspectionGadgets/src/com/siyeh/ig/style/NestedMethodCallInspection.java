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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NestedMethodCallInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreFieldInitializations = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("nested.method.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("nested.method.call.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("nested.method.call.ignore.option"),
      this, "m_ignoreFieldInitializations");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedMethodCallVisitor();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class NestedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiExpression outerExpression = expression;
      while (outerExpression != null && outerExpression.getParent() instanceof PsiExpression) {
        outerExpression = (PsiExpression)outerExpression.getParent();
      }
      if (outerExpression == null) {
        return;
      }
      final PsiElement parent = outerExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return;
      }
      if (ExpressionUtils.isConstructorInvocation(grandParent)) {
        //ignore nested method calls at the start of a constructor,
        //where they can't be extracted
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}