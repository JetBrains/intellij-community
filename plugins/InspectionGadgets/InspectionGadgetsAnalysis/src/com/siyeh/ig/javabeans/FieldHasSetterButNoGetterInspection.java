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
package com.siyeh.ig.javabeans;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class FieldHasSetterButNoGetterInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "field.has.setter.but.no.getter.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.has.setter.but.no.getter.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldHasSetterButNoGetterVisitor();
  }

  private static class FieldHasSetterButNoGetterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      final String propertyName = PropertyUtilBase.suggestPropertyName(field);
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      final PsiClass containingClass = field.getContainingClass();
      final PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propertyName, isStatic, false);
      if (setter == null) {
        return;
      }
      final PsiMethod getter = PropertyUtilBase.findPropertyGetter(containingClass, propertyName, isStatic, false);
      if (getter != null) {
        return;
      }
      registerFieldError(field);
    }
  }
}