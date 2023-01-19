/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class MakeInitializerExplicitFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message(
      "make.initialization.explicit.quickfix");
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement fieldName = descriptor.getPsiElement();
    final PsiElement parent = fieldName.getParent();
    if (!(parent instanceof PsiField)) return;
    final PsiField field = (PsiField)parent;
    if (field.getInitializer() != null) {
      return;
    }
    final PsiType type = field.getType();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression initializer = factory.createExpressionFromText(TypeUtils.getDefaultValue(type), field);
    field.setInitializer(initializer);
  }
}