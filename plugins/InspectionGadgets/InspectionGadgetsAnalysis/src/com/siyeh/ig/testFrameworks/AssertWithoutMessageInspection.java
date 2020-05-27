/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.testFrameworks;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AssertWithoutMessageInspection extends BaseInspection {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertionsWithoutMessagesVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("asserts.without.messages.problem.descriptor");
  }

  private static class AssertionsWithoutMessagesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      AssertHint assertHint = AssertHint.create(expression, methodName -> AssertHint.JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT.get(methodName));
      if (assertHint == null) {
        return;
      }
      PsiExpression message = assertHint.getMessage();
      if (message == null) {
        registerMethodCallError(expression);
      }
    }
  }
}