/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.finalization;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class NoExplicitFinalizeCallsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "FinalizeCalledExplicitly";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "finalize.called.explicitly.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.called.explicitly.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NoExplicitFinalizeCallsVisitor();
  }

  private static class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, null, PsiType.VOID, HardcodedMethodConstants.FINALIZE)) {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod == null || MethodUtils.isFinalize(containingMethod)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}