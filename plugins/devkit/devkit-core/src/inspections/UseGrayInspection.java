// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToGrayQuickFix;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class UseGrayInspection extends DevKitInspectionBase {

  private static final String AWT_COLOR_CLASS_NAME = Color.class.getName();
  private static final String GRAY_CLASS_NAME = Gray.class.getName();

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (isAwtRgbColorConstructor(expression)) {
          Integer grayValue = getGrayValue(getConstructorParams(expression));
          if (grayValue != null && grayClassAccessible(expression)) {
            holder.registerProblem(expression,
                                   DevKitBundle.message("inspections.use.gray.awt.color.used.name"),
                                   new ConvertToGrayQuickFix(grayValue));
          }
        }
      }
    };
  }

  private static boolean isAwtRgbColorConstructor(PsiNewExpression expression) {
    PsiExpression[] constructorParams = getConstructorParams(expression);
    if (constructorParams == null) return false;
    PsiType type = expression.getType();
    if (type == null) return false;
    return constructorParams.length == 3 && AWT_COLOR_CLASS_NAME.equals(type.getCanonicalText());
  }

  @NotNull
  private static PsiExpression @Nullable [] getConstructorParams(@NotNull PsiNewExpression expression) {
    PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) return null;
    return arguments.getExpressions();
  }

  @Nullable
  private static Integer getGrayValue(@NotNull PsiExpression @Nullable [] constructorParams) {
    if (constructorParams == null) return null;
    PsiExpression redParam = constructorParams[0];
    Integer red = evaluateColorValue(redParam);
    if (red == null) return null;
    PsiExpression greenParam = constructorParams[1];
    PsiExpression blueParam = constructorParams[2];
    return 0 <= red && red < 256 && red.equals(evaluateColorValue(greenParam)) && red.equals(evaluateColorValue(blueParam)) ? red : null;
  }

  @Nullable
  private static Integer evaluateColorValue(@NotNull PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) {
      Object evaluatedColorValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
      if (evaluatedColorValue != null) {
        try {
          return Integer.parseInt(evaluatedColorValue.toString());
        }
        catch (Exception e) {
          // ignore
        }
      }
    }
    return null;
  }

  private static boolean grayClassAccessible(@NotNull PsiElement checkedPlace) {
    return JavaPsiFacade.getInstance(checkedPlace.getProject()).findClass(GRAY_CLASS_NAME, checkedPlace.getResolveScope()) != null;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "InspectionUsingGrayColors";
  }
}
