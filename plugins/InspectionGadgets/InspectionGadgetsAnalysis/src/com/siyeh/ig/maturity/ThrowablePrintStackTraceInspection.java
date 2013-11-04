/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ThrowablePrintStackTraceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInTestCode = false;

  @Override
  @NotNull
  public String getID() {
    return "CallToPrintStackTrace";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("printstacktrace.call.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("printstacktrace.call.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("ignore.in.test.code"), this, "ignoreInTestCode");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowablePrintStackTraceVisitor();
  }

  private class ThrowablePrintStackTraceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final String methodName = MethodCallUtils.getMethodName(expression);
      if (!HardcodedMethodConstants.PRINT_STACK_TRACE.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList.getExpressions().length != 0) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String name = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_THROWABLE.equals(name)) {
        return;
      }
      if (ignoreInTestCode && TestUtils.isInTestCode(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}