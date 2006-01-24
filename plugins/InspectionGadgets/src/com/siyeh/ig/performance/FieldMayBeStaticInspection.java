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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeStaticInspection extends FieldInspection {

  private final MakeStaticFix fix = new MakeStaticFix();

  public String getGroupDisplayName() {
    return GroupNames.PERFORMANCE_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FieldMayBeStaticVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class MakeStaticFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("make.static.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiJavaToken m_fieldNameToken =
        (PsiJavaToken)descriptor.getPsiElement();
      final PsiField field = (PsiField)m_fieldNameToken.getParent();
      assert field != null;
      final PsiModifierList modifiers = field.getModifierList();
      modifiers.setModifierProperty(PsiModifier.STATIC, true);
    }
  }

  private static class FieldMayBeStaticVisitor extends BaseInspectionVisitor {

    public void visitField(@NotNull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(initializer)) {
        return;
      }
      if (!canBeStatic(initializer)) {
        return;
      }
      final PsiType type = field.getType();

      if (!ClassUtils.isImmutable(type)) {
        return;
      }
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null
          && !containingClass.hasModifierProperty(PsiModifier.STATIC)
          && containingClass.getContainingClass() != null
          && !PsiUtil.isCompileTimeConstant(field)) {
        // inner class cannot have static declarations
        return;
      }
      registerFieldError(field);
    }

    private static boolean canBeStatic(PsiExpression initializer) {
      final CanBeStaticVisitor canBeStaticVisitor =
        new CanBeStaticVisitor();
      initializer.accept(canBeStaticVisitor);
      return canBeStaticVisitor.canBeStatic();
    }
  }
}
