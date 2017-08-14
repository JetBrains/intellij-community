/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class OctalAndDecimalIntegersMixedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "OctalAndDecimalIntegersInSameArray";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("octal.and.decimal.integers.in.same.array.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("octal.and.decimal.integers.in.same.array.problem.descriptor");
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{
      new ConvertOctalLiteralToDecimalFix(),
      new RemoveLeadingZeroFix()
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OctalAndDecimalIntegersMixedVisitor();
  }

  private static class OctalAndDecimalIntegersMixedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiExpression[] initializers = expression.getInitializers();
      boolean hasDecimalLiteral = false;
      boolean hasOctalLiteral = false;
      for (PsiExpression initializer : initializers) {
        initializer = ParenthesesUtils.stripParentheses(initializer);
        if (initializer instanceof PsiLiteralExpression) {
          final PsiLiteralExpression literal = (PsiLiteralExpression)initializer;
          if (isDecimalLiteral(literal)) {
            hasDecimalLiteral = true;
          }
          if (isOctalLiteral(literal)) {
            hasOctalLiteral = true;
          }
        }
      }
      if (hasOctalLiteral && hasDecimalLiteral) {
        registerError(expression);
      }
    }

    private static boolean isDecimalLiteral(PsiLiteralExpression literal) {
      final PsiType type = literal.getType();
      if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
        return false;
      }
      final String text = literal.getText();
      return text.charAt(0) != '0';
    }

    private static boolean isOctalLiteral(PsiLiteralExpression literal) {
      final PsiType type = literal.getType();
      if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
        return false;
      }
      @NonNls final String text = literal.getText();
      if (text.charAt(0) != '0' || text.length() < 2) {
        return false;
      }
      final char c1 = text.charAt(1);
      return c1 == '_' || (c1 >= '0' && c1 <= '7');
    }
  }
}