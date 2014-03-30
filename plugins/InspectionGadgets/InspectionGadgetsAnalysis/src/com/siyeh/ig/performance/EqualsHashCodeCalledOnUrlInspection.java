/*
 * Copyright 2007-2012 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsHashCodeCalledOnUrlInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.hashcode.called.on.url.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.hashcode.called.on.url.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsHashCodeCalledOnUrlVisitor();
  }

  private static class EqualsHashCodeCalledOnUrlVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiMethod method = expression.resolveMethod();
      if (!MethodUtils.isEquals(method) && !MethodUtils.isHashCode(method)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!ExpressionUtils.hasType(qualifier, "java.net.URL")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}