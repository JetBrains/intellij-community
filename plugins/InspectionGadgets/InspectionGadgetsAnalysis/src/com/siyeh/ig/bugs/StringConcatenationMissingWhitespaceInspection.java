/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationMissingWhitespaceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreNonStringLiterals = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.option"),
                                          this, "ignoreNonStringLiterals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationMissingWhitespaceVisitor();
  }

  private class StringConcatenationMissingWhitespaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType) || !ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final boolean formatCall = FormatUtils.isFormatCallArgument(expression);
      final PsiExpression[] operands = expression.getOperands();
      PsiExpression lhs = operands[0];
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression rhs = operands[i];
        if (isMissingWhitespace(lhs, rhs, formatCall)) {
          final PsiJavaToken token = expression.getTokenBeforeOperand(rhs);
          if (token != null) {
            registerError(token);
          }
        }
        lhs = rhs;
      }
    }

    private boolean isMissingWhitespace(PsiExpression lhs, PsiExpression rhs, boolean formatCall) {
      @NonNls final String lhsLiteral = computeStringValue(lhs);
      if (lhsLiteral != null) {
        final int length = lhsLiteral.length();
        if (length == 0 || formatCall && lhsLiteral.endsWith("%n")) {
          return false;
        }
        final char c = lhsLiteral.charAt(length - 1);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals && ExpressionUtils.hasStringType(lhs)) {
        return false;
      }
      @NonNls final String rhsLiteral = computeStringValue(rhs);
      if (rhsLiteral != null) {
        if (rhsLiteral.isEmpty() || formatCall && rhsLiteral.startsWith("%n")) {
          return false;
        }
        final char c = rhsLiteral.charAt(0);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals && ExpressionUtils.hasStringType(rhs)) {
        return false;
      }
      return true;
    }

    @Nullable
    public String computeStringValue(@Nullable PsiExpression expression) {
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      return value == null ? null : value.toString();
    }
  }
}
