// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConstantExpressionInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final int MAX_RESULT_LENGTH_TO_DISPLAY = 50;
  private static final int MAX_EXPRESSION_LENGTH = 200;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitUnaryExpression(PsiUnaryExpression expression) {
        handle(expression);
      }

      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        handle(expression);
      }

      void handle(PsiExpression expression) {
        // inspection disabled for long expressions because of performance issues on
        // relatively common large string expressions.
        if (expression.getTextLength() > MAX_EXPRESSION_LENGTH) return;
        if (expression.getType() == null) return;
        if (!PsiUtil.isConstantExpression(expression)) return;
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpression && PsiUtil.isConstantExpression((PsiExpression)parent)) return;
        try {
          final Object value = ExpressionUtils.computeConstantExpression(expression, true);
          if (value != null) {
            String valueText = getValueText(value);
            if (!expression.textMatches(valueText)) {
              String message = valueText.length() > MAX_RESULT_LENGTH_TO_DISPLAY ?
                               InspectionGadgetsBundle.message("inspection.constant.expression.display.name") :
                               InspectionGadgetsBundle.message("inspection.constant.expression.message", valueText);
              holder.registerProblem(expression, message,
                                     new ComputeConstantValueFix(expression, valueText));
            }
          }
        }
        catch (ConstantEvaluationOverflowException ignore) {
        }
      }
    };
  }

  private static class ComputeConstantValueFix implements LocalQuickFix {
    private final String myText;
    private final String myValueText;

    public ComputeConstantValueFix(PsiExpression expression, String valueText) {
      myText = PsiExpressionTrimRenderer.render(expression);
      myValueText = valueText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myValueText.length() > MAX_RESULT_LENGTH_TO_DISPLAY) {
        return InspectionGadgetsBundle.message("inspection.constant.expression.fix.name", myText);
      }
      return InspectionGadgetsBundle.message("inspection.constant.expression.fix.name.with.value", myText, myValueText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.constant.expression.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getStartElement();
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      @NonNls final String newExpression = getValueText(value);
      PsiReplacementUtil.replaceExpression(expression, newExpression, new CommentTracker());
    }
  }

  private static String getValueText(Object value) {
    @NonNls final String newExpression;
    if (value instanceof String) {
      final String string = (String)value;
      newExpression = '"' + StringUtil.escapeStringCharacters(string) + '"';
    }
    else if (value instanceof Character) {
      newExpression = '\'' + StringUtil.escapeStringCharacters(value.toString()) + '\'';
    }
    else if (value instanceof Long) {
      newExpression = value.toString() + 'L';
    }
    else if (value instanceof Double) {
      final double v = ((Double)value).doubleValue();
      if (Double.isNaN(v)) {
        newExpression = "java.lang.Double.NaN";
      }
      else if (Double.isInfinite(v)) {
        if (v > 0.0) {
          newExpression = "java.lang.Double.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Double.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Double.toString(v);
      }
    }
    else if (value instanceof Float) {
      final float v = ((Float)value).floatValue();
      if (Float.isNaN(v)) {
        newExpression = "java.lang.Float.NaN";
      }
      else if (Float.isInfinite(v)) {
        if (v > 0.0F) {
          newExpression = "java.lang.Float.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Float.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Float.toString(v) + 'f';
      }
    }
    else if (value == null) {
      newExpression = "null";
    }
    else {
      newExpression = String.valueOf(value);
    }
    return newExpression;
  }
}
