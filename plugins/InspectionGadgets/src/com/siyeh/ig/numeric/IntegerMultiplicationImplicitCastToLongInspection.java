/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class IntegerMultiplicationImplicitCastToLongInspection extends
                                                               BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  private static final Set<String> s_typesToCheck = new HashSet<String>(4);

  static {
    s_typesToCheck.add("int");
    s_typesToCheck.add("short");
    s_typesToCheck.add("byte");
    s_typesToCheck.add("char");
  }

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNonOverflowingCompileTimeConstants = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "integer.multiplication.implicit.cast.to.long.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "integer.multiplication.implicit.cast.to.long.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "integer.multiplication.implicit.cast.to.long.option"),
                                          this, "ignoreNonOverflowingCompileTimeConstants");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerMultiplicationImplicitlyCastToLongVisitor();
  }

  private class IntegerMultiplicationImplicitlyCastToLongVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK)
          && !tokenType.equals(JavaTokenType.LTLT)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!isNonLongInteger(type)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (!isNonLongInteger(rhsType)) {
        return;
      }
      final PsiExpression context = getContainingExpression(expression);
      if (context == null) {
        return;
      }
      final PsiType contextType =
        ExpectedTypeUtils.findExpectedType(context, true);
      if (contextType == null) {
        return;
      }
      if (!contextType.equals(PsiType.LONG)) {
        return;
      }
      try {
        final Object result =
          ExpressionUtils.computeConstantExpression(expression,
                                                    true);
        if (ignoreNonOverflowingCompileTimeConstants &&
            result != null) {
          return;
        }
      }
      catch (ConstantEvaluationOverflowException ignore) {
      }
      registerError(expression);
    }

    private PsiExpression getContainingExpression(
      PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiBinaryExpression ||
          parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiPrefixExpression ||
          parent instanceof PsiConditionalExpression) {
        return getContainingExpression((PsiExpression)parent);
      }
      return expression;
    }

    private boolean isNonLongInteger(PsiType type) {
      if (type == null) {
        return false;
      }
      final String text = type.getCanonicalText();
      return text != null && s_typesToCheck.contains(text);
    }
  }
}