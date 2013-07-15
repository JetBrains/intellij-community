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
package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class TypeParameterHidesVisibleTypeInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "type.parameter.hides.visible.type.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message(
      "type.parameter.hides.visible.type.problem.descriptor",
      aClass.getQualifiedName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeParameterHidesVisibleTypeVisitor();
  }

  private static class TypeParameterHidesVisibleTypeVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String unqualifiedClassName = parameter.getName();

      final JavaPsiFacade manager = JavaPsiFacade.getInstance(parameter.getProject());
      final PsiFile containingFile = parameter.getContainingFile();
      final PsiResolveHelper resolveHelper = manager.getResolveHelper();
      final PsiClass aClass =
        resolveHelper.resolveReferencedClass(unqualifiedClassName,
                                             containingFile);
      if (aClass == null) {
        return;
      }
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      registerError(identifier, aClass);
    }
  }
}