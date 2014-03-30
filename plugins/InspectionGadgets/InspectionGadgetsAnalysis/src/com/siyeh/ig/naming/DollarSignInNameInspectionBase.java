/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class DollarSignInNameInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "dollar.sign.in.name.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "dollar.sign.in.name.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DollarSignInNameVisitor();
  }

  private static class DollarSignInNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerVariableError(variable);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerMethodError(method);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      //note: no call to super, to avoid drill-down
      final String name = aClass.getName();
      if (name == null) {
        return;
      }
      if (name.indexOf((int)'$') < 0) {
        return;
      }
      registerClassError(aClass);
    }
  }
}
