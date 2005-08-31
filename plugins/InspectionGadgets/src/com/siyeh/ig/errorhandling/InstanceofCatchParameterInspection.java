/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class InstanceofCatchParameterInspection extends ExpressionInspection {

  public String getGroupDisplayName() {
    return GroupNames.ERRORHANDLING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofCatchParameterVisitor();
  }

  private static class InstanceofCatchParameterVisitor extends BaseInspectionVisitor {

    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression exp) {
      super.visitInstanceOfExpression(exp);
      if (!ControlFlowUtils.isInCatchBlock(exp)) {
        return;
      }

      final PsiExpression operand = exp.getOperand();
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression ref = (PsiReferenceExpression)operand;
      final PsiElement referent = ref.resolve();
      if (!(referent instanceof PsiParameter)) {
        return;
      }
      if (!(((PsiParameter)referent).getDeclarationScope() instanceof PsiCatchSection)) {
        return;
      }
      registerError(exp);
    }

  }
}
