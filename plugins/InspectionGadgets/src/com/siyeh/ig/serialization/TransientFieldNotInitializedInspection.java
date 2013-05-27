/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class TransientFieldNotInitializedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "transient.field.not.initialized.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "transient.field.not.initialized.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReadObjectInitializationVisitor();
  }

  private static class ReadObjectInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.TRANSIENT)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (!SerializationUtils.isSerializable(containingClass)) {
        return;
      }
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null &&
          !isInitializedInInitializer(field, containingClass) &&
          !isInitializedInConstructors(field, containingClass)) {
        return;
      }
      if (SerializationUtils.hasReadObject(containingClass)) {
        return;
      }
      registerFieldError(field);
    }

    private static boolean isInitializedInConstructors(
      @NotNull PsiField field, @NotNull PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        return false;
      }
      for (final PsiMethod constructor : constructors) {
        if (!InitializationUtils.methodAssignsVariableOrFails(
          constructor, field)) {
          return false;
        }
      }
      return true;
    }

    private static boolean isInitializedInInitializer(
      @NotNull PsiField field, @NotNull PsiClass aClass) {
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiCodeBlock body = initializer.getBody();
        if (InitializationUtils.blockAssignsVariableOrFails(body,
                                                            field)) {
          return true;
        }
      }
      return false;
    }
  }
}