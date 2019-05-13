/*
 * Copyright 2007-2014 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeFieldFinalFix extends InspectionGadgetsFix {

  private final String fieldName;

  private MakeFieldFinalFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Nullable
  public static InspectionGadgetsFix buildFix(PsiField field) {
    if (!FinalUtils.canBeFinal(field)) {
      return null;
    }
    final String name = field.getName();
    return new MakeFieldFinalFix(name);
  }

  @NotNull
  public static InspectionGadgetsFix buildFixUnconditional(PsiField field) {
    return new MakeFieldFinalFix(field.getName());
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("make.field.final.quickfix",
                                           fieldName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Make final";
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiField field;
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)element;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      field = (PsiField)target;
    }
    else {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiField)) {
        return;
      }
      field = (PsiField)parent;
    }
    field.normalizeDeclaration();
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.VOLATILE, false);
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
  }
}