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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MagicNumberInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInHashCode = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreInTestCode = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreInAnnotations = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("magic.number.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("magic.number.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("magic.number.ignore.option"), "ignoreInHashCode");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.test.code"), "ignoreInTestCode");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.annotations"),"ignoreInAnnotations");
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceConstantFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MagicNumberVisitor();
  }

  private class MagicNumberVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(type)) {
        return;
      }
      if (PsiType.CHAR.equals(type)) {
        return;
      }
      if (isSpecialCaseLiteral(expression)) {
        return;
      }
      if (ExpressionUtils.isDeclaredConstant(expression)) {
        return;
      }
      if (ignoreInHashCode) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (MethodUtils.isHashCode(containingMethod)) {
          return;
        }
      }
      if (ignoreInTestCode && TestUtils.isInTestCode(expression)) {
        return;
      }
      final boolean insideAnnotation = AnnotationUtil.isInsideAnnotation(expression);
      if (ignoreInAnnotations && insideAnnotation) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPrefixExpression) {
        registerError(parent);
      }
      else {
        registerError(expression);
      }
    }

    private boolean isSpecialCaseLiteral(PsiLiteralExpression expression) {
      final Object object = ExpressionUtils.computeConstantExpression(expression);
      if (object instanceof Integer) {
        final int i = ((Integer)object).intValue();
        return i >= 0 && i <= 10 || i == 100 || i == 1000;
      }
      else if (object instanceof Long) {
        final long l = ((Long)object).longValue();
        return l >= 0L && l <= 2L;
      }
      else if (object instanceof Double) {
        final double d = ((Double)object).doubleValue();
        return d == 1.0 || d == 0.0;
      }
      else if (object instanceof Float) {
        final float f = ((Float)object).floatValue();
        return f == 1.0f || f == 0.0f;
      }
      return false;
    }
  }
}
