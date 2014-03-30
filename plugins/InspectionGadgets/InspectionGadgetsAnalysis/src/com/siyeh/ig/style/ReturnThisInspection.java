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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ReturnThisInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ReturnOfThis";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.this.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "return.this.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnThisVisitor();
  }

  private static class ReturnThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression thisValue) {
      super.visitThisExpression(thisValue);
      if (thisValue.getQualifier() != null) {
        return;
      }
      PsiElement parent = thisValue.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
              parent instanceof PsiConditionalExpression ||
              parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiReturnStatement)) {
        return;
      }
      registerError(thisValue);
    }
  }
}