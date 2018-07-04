/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RedundantFieldInitializationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnNull = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("redundant.field.initialization.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.field.initialization.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Only warn on initialization to null", this, "onlyWarnOnNull");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantFieldInitializationFix();
  }

  private static class RedundantFieldInitializationFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.field.initialization.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      descriptor.getPsiElement().delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantFieldInitializationVisitor();
  }

  private class RedundantFieldInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!field.hasInitializer() || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        return;
      }
      final String text = initializer.getText();
      final PsiType type = field.getType();
      if (PsiType.BOOLEAN.equals(type)) {
        if (onlyWarnOnNull || !PsiKeyword.FALSE.equals(text)) {
          return;
        }
      }
      else if (type instanceof PsiPrimitiveType) {
        if (onlyWarnOnNull || !ExpressionUtils.isZero(initializer)) {
          return;
        }
      }
      else if (!PsiType.NULL.equals(initializer.getType())) {
        return;
      }
      if (initializer instanceof PsiReferenceExpression ||
          !PsiTreeUtil.findChildrenOfType(initializer, PsiReferenceExpression.class).isEmpty()) {
        return;
      }
      if (isAssignmentInInitializerOverwritten(field)) {
        return;
      }
      registerError(initializer, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }

    private boolean isAssignmentInInitializerOverwritten(@NotNull PsiField field) {
      // JLS 12.5. Creation of New Class Instances
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (PsiClassInitializer classInitializer : initializers) {
        if (classInitializer.hasModifierProperty(PsiModifier.STATIC) == isStatic &&
            classInitializer.getTextOffset() < field.getTextOffset() &&
            VariableAccessUtils.variableIsAssigned(field, classInitializer)) {
          return true;
        }
      }
      return false;
    }
  }
}