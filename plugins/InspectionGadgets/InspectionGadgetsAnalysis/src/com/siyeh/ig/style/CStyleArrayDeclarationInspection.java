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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class CStyleArrayDeclarationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "c.style.array.declaration.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "c.style.array.declaration.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new CStyleArrayDeclarationFix();
  }

  private static class CStyleArrayDeclarationFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "c.style.array.declaration.replace.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement nameElement = descriptor.getPsiElement();
      final PsiVariable var = (PsiVariable)nameElement.getParent();
      assert var != null;
      var.normalizeDeclaration();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CStyleArrayDeclarationVisitor();
  }

  private static class CStyleArrayDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable var) {
      super.visitVariable(var);
      final PsiType declaredType = var.getType();
      if (declaredType.getArrayDimensions() == 0) {
        return;
      }
      final PsiTypeElement typeElement = var.getTypeElement();
      if (typeElement == null) {
        return; // Could be true for enum constants.
      }
      final PsiType elementType = typeElement.getType();
      if (elementType.equals(declaredType)) {
        return;
      }
      registerVariableError(var);
    }
  }
}