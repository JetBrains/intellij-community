// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.LiteralFormatUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnpredictableBigDecimalConstructorCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField") public boolean ignoreReferences = true;
  @SuppressWarnings("PublicField") public boolean ignoreComplexLiterals = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unpredictable.big.decimal.constructor.call.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
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
    final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (firstArgument instanceof PsiLiteralExpression) {
      return new ReplaceDoubleArgumentWithStringFix("new BigDecimal(\"" + getLiteralText((PsiLiteralExpression)firstArgument) + "\")");
    }
    if (arguments.length == 1 && firstArgument != null) {
      return new ReplaceDoubleArgumentWithStringFix("BigDecimal.valueOf(" + firstArgument.getText() + ')');
    }
    return null;
  }

  static String getLiteralText(PsiLiteralExpression firstArgument) {
    final String text = LiteralFormatUtil.removeUnderscores(firstArgument.getText());
    final char c = text.charAt(text.length() - 1);
    return c == 'd' || c == 'D' || c == 'f' || c == 'F'
           ? text.substring(0, text.length() - 1)
           : text;
  }

  private static class ReplaceDoubleArgumentWithStringFix extends InspectionGadgetsFix {

    private final String argumentText;

    ReplaceDoubleArgumentWithStringFix(@NonNls String argumentText) {
      this.argumentText = argumentText;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", argumentText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "BigDecimal.valueOf()");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiNewExpression newExpression = (PsiNewExpression)element.getParent();
      if (!isStillValid(newExpression)) {
        return;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (firstArgument instanceof PsiLiteralExpression) {
          PsiReplacementUtil.replaceExpression(firstArgument, '"' + getLiteralText((PsiLiteralExpression)firstArgument) + '"');
      }
      else if (arguments.length == 1 && firstArgument != null) {
        PsiReplacementUtil.replaceExpression(newExpression, "java.math.BigDecimal.valueOf(" + firstArgument.getText() + ')');
      }
    }

    private static boolean isStillValid(PsiNewExpression newExpression) {
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) return false;
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      if (parameters.length == 0) return false;
      if (!PsiType.DOUBLE.equals(parameters[0].getType())) return false;
      return true;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnpredictableBigDecimalConstructorCallVisitor();
  }

  private class UnpredictableBigDecimalConstructorCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (!ConstructionUtils.isReferenceTo(classReference, "java.math.BigDecimal")) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
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