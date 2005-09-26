/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class CStyleArrayDeclarationInspection extends ClassInspection {

  private final CStyleArrayDeclarationFix fix = new CStyleArrayDeclarationFix();

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class CStyleArrayDeclarationFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement nameElement = descriptor.getPsiElement();
      final PsiVariable var = (PsiVariable)nameElement.getParent();
      assert var != null;
      var.normalizeDeclaration();
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new CStyleArrayDeclarationVisitor();
  }

  private static class CStyleArrayDeclarationVisitor
            extends BaseInspectionVisitor{

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

