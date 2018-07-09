/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class InstanceofCatchParameterInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "instanceof.catch.parameter.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "instanceof.catch.parameter.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofCatchParameterVisitor();
  }

  private static class InstanceofCatchParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression exp) {
      super.visitInstanceOfExpression(exp);
      if (!ControlFlowUtils.isInCatchBlock(exp)) {
        return;
      }
      PsiTypeElement typeElement = exp.getCheckType();
      if (typeElement == null || !InheritanceUtil.isInheritor(typeElement.getType(), CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(exp.getOperand());
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression ref = (PsiReferenceExpression)operand;
      final PsiElement referent = ref.resolve();
      if (!(referent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)referent;
      if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) {
        return;
      }
      registerError(operand);
    }
  }
}
