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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

public class PublicFieldInspectionBase extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreEnums = false;

  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet("org.junit.runners.Parameterized.Parameter");

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("public.field.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "public.field.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldVisitor();
  }

  private class PublicFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(field, ignorableAnnotations, 0)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiType type = field.getType();
        if (ClassUtils.isImmutable(type)) {
          return;
        }
        if (ignoreEnums) {
          if (type instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if (aClass != null && aClass.isEnum()) {
              return;
            }
          }
        }
      }
      registerFieldError(field, field);
    }
  }
}