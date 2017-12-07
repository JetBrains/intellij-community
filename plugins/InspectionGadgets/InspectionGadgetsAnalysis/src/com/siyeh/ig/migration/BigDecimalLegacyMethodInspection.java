/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class BigDecimalLegacyMethodInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bigdecimal.legacy.method.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bigdecimal.legacy.method.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (!(value instanceof  Integer)) {
      return null;
    }
    final int roundingMode = ((Integer)value).intValue();
    if (roundingMode < 0 || roundingMode > 7) {
      return null;
    }
    return new BigDecimalLegacyMethodFix();
  }

  private static class BigDecimalLegacyMethodFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("bigdecimal.legacy.method.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2 && arguments.length != 3) {
        return;
      }
      final PsiExpression argument = arguments[arguments.length - 1];
      final Object value = ExpressionUtils.computeConstantExpression(argument);
      if (!(value instanceof Integer)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final int roundingMode = (Integer)value;
      switch (roundingMode) {
        case 0:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.UP", commentTracker);
          break;
        case 1:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.DOWN", commentTracker);
          break;
        case 2:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.CEILING", commentTracker);
          break;
        case 3:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.FLOOR", commentTracker);
          break;
        case 4:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_UP", commentTracker);
          break;
        case 5:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_DOWN", commentTracker);
          break;
        case 6:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.HALF_EVEN", commentTracker);
          break;
        case 7:
          PsiReplacementUtil.replaceExpressionAndShorten(argument, "java.math.RoundingMode.UNNECESSARY", commentTracker);
          break;
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BigDecimalLegacyMethodVisitor();
  }

  private static class BigDecimalLegacyMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"setScale".equals(name) && !"divide".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (PsiUtilCore.hasErrorElementChild(argumentList)) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2 && arguments.length != 3) {
        return;
      }
      final PsiExpression argument = arguments[arguments.length - 1];
      if (!PsiType.INT.equals(argument.getType())) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, "java.math.BigDecimal")) {
        return;
      }
      registerMethodCallError(expression, argument);
    }
  }
}
