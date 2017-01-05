/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public abstract class ConstantAssertArgumentInspectionBase extends BaseInspection {
  @NonNls
  private static final Set<String> ASSERT_METHODS = new HashSet<>();

  static {
    ASSERT_METHODS.add("assertTrue");
    ASSERT_METHODS.add("assertFalse");
    ASSERT_METHODS.add("assertNull");
    ASSERT_METHODS.add("assertNotNull");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.junit.assert.argument.problem.descriptor");
  }

  protected abstract boolean checkTestNG();

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantAssertArgumentVisitor();
  }

  private class ConstantAssertArgumentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      AssertHint assertHint = AssertHint.create(expression, methodName -> ASSERT_METHODS.contains(methodName) ? 1 : null, checkTestNG());
      if (assertHint == null) {
        return;
      }

      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[assertHint.getArgIndex()];
      if (!PsiUtil.isConstantExpression(argument)) {
        return;
      }
      registerError(argument);
    }
  }
}
