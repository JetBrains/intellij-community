/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ChainedEqualityInspection extends BaseInspection {

  @NotNull
  public String getID() {
    return "ChainedEqualityComparisons";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("chained.equality.comparisons.display.name");
  }

  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("chained.equality.comparisons.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ChainedEqualityVisitor();
  }

  private static class ChainedEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.EQEQ != tokenType && JavaTokenType.NE != tokenType) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length < 3) {
        return;
      }
      registerError(expression);
    }
  }
}