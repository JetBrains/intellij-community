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
package com.siyeh.ig.jdk;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AssertAsNameInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getID() {
    return "AssertAsIdentifier";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "use.assert.as.identifier.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.assert.as.identifier.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertAsNameVisitor();
  }

  private static class AssertAsNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final String variableName = variable.getName();
      if (!PsiKeyword.ASSERT.equals(variableName)) {
        return;
      }
      registerVariableError(variable);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (!PsiKeyword.ASSERT.equals(name)) {
        return;
      }
      registerMethodError(method);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      //note: no call to super, to avoid drill-down
      final String name = aClass.getName();
      if (!PsiKeyword.ASSERT.equals(name)) {
        return;
      }
      final PsiTypeParameterList params = aClass.getTypeParameterList();
      if (params != null) {
        params.accept(this);
      }
      registerClassError(aClass);
    }

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String name = parameter.getName();
      if (!PsiKeyword.ASSERT.equals(name)) {
        return;
      }
      registerTypeParameterError(parameter);
    }
  }
}
