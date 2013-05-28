/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UnaryPlusInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unary.plus.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unary.plus.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnaryPlusVisitor();
  }

  private static class UnaryPlusVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(
      PsiPrefixExpression prefixExpression) {
      super.visitPrefixExpression(prefixExpression);
      final PsiJavaToken token = prefixExpression.getOperationSign();
      final IElementType tokenType = token.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) {
        return;
      }
      registerError(token);
    }
  }
}