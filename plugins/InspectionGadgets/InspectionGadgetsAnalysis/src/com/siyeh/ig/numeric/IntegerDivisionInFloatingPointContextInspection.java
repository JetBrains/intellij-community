/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class IntegerDivisionInFloatingPointContextInspection
  extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  private static final Set<String> s_integralTypes = new HashSet<>(10);

  static {
    s_integralTypes.add("int");
    s_integralTypes.add("long");
    s_integralTypes.add("short");
    s_integralTypes.add("byte");
    s_integralTypes.add("char");
    s_integralTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_LONG);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "integer.division.in.floating.point.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "integer.division.in.floating.point.context.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerDivisionInFloatingPointContextVisitor();
  }

  private static class IntegerDivisionInFloatingPointContextVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIV)) {
        return;
      }
      for (PsiExpression operand : expression.getOperands()) {
        if (!isIntegral(operand.getType())) {
          return;
        }
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
      if (!(contextType.equals(PsiType.FLOAT)
            || contextType.equals(PsiType.DOUBLE))) {
        return;
      }
      registerError(expression);
    }

    private static boolean isIntegral(PsiType type) {
      if (type == null) {
        return false;
      }
      final String text = type.getCanonicalText();
      return text != null && s_integralTypes.contains(text);
    }

    private static PsiExpression getContainingExpression(
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
  }
}