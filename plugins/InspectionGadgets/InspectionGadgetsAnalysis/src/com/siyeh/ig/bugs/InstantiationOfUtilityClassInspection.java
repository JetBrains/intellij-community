/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class InstantiationOfUtilityClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "instantiation.utility.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "instantiation.utility.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstantiationOfUtilityClassVisitor();
  }

  private static class InstantiationOfUtilityClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass == null) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      registerNewExpressionError(expression);
    }
  }
}