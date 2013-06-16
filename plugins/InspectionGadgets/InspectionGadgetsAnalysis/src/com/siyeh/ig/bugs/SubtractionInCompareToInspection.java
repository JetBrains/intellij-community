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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class SubtractionInCompareToInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SubtractionInCompareToVisitor();
  }

  private static class SubtractionInCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.MINUS)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class);
      if (method == null) {
        return;
      }
      if (MethodUtils.isCompareTo(method)) {
        registerError(expression);
      }
      final PsiClass comparatorClass = ClassUtils.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, expression);
      if (comparatorClass == null) {
        return;
      }
      final PsiMethod[] methods = comparatorClass.findMethodsByName("compare", false);
      assert methods.length == 1;
      final PsiMethod compareMethod = methods[0];
      if (!PsiSuperMethodUtil.isSuperMethod(method, compareMethod)) {
        return;
      }
      registerError(expression);
    }
  }
}