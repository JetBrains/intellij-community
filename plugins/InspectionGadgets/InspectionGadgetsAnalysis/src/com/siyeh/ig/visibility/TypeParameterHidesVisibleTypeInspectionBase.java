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
package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class TypeParameterHidesVisibleTypeInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("type.parameter.hides.visible.type.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiTypeParameter) {
      return InspectionGadgetsBundle.message("type.parameter.hides.type.parameter.problem.descriptor", aClass.getName());
    }
    else {
      String name = aClass.getQualifiedName();
      if (name == null) {
        name = aClass.getName();
      }
      return InspectionGadgetsBundle.message("type.parameter.hides.visible.type.problem.descriptor", name);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeParameterHidesVisibleTypeVisitor();
  }

  private static class TypeParameterHidesVisibleTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeParameter(PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final String unqualifiedClassName = parameter.getName();
      PsiTypeParameterListOwner context = parameter.getOwner();
      if (context == null) {
        return;
      }
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(parameter.getProject()).getResolveHelper();
      while (true) {
        if (context.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        context = PsiTreeUtil.getParentOfType(context, PsiTypeParameterListOwner.class);
        if (context == null) {
          return;
        }
        final PsiClass aClass = resolveHelper.resolveReferencedClass(unqualifiedClassName, context);
        if (aClass instanceof PsiTypeParameter) {
          final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
          final PsiTypeParameterListOwner owner = typeParameter.getOwner();
          if (owner == null) {
            return;
          }
          if (!owner.equals(context)) {
            continue;
          }
        }
        if (aClass != null) {
          registerClassError(parameter, aClass);
          return;
        }
      }
    }
  }
}