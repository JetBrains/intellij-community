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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocalVariableHidingMemberVariableInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreStaticMethods = true;

  @Override
  @NotNull
  public String getID() {
    return "LocalVariableHidesMemberVariable";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("local.variable.hides.member.variable.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("local.variable.hides.member.variable.problem.descriptor", aClass.getName());
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("field.name.hides.in.superclass.ignore.option"), "m_ignoreInvisibleFields");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("local.variable.hides.member.variable.ignore.option"), "m_ignoreStaticMethods");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LocalVariableHidingMemberVariableVisitor();
  }

  private class LocalVariableHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (m_ignoreStaticMethods) {
        final PsiMember member = PsiTreeUtil.getParentOfType(variable, PsiMethod.class, PsiClassInitializer.class);
        if (member != null && member.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }
      final PsiClass aClass = checkFieldNames(variable);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiCatchSection) && !(declarationScope instanceof PsiForeachStatement)) {
        return;
      }
      if (m_ignoreStaticMethods) {
        final PsiMember member = PsiTreeUtil.getParentOfType(variable, PsiMethod.class, PsiClassInitializer.class);
        if (member != null && member.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }
      final PsiClass aClass = checkFieldNames(variable);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }

    @Nullable
    private PsiClass checkFieldNames(PsiVariable variable) {
      PsiClass aClass = ClassUtils.getContainingClass(variable);
      final String variableName = variable.getName();
      if (variableName == null) {
        return null;
      }
      while (aClass != null) {
        final PsiField[] fields = aClass.getAllFields();
        for (PsiField field : fields) {
          final String fieldName = field.getName();
          if (!variableName.equals(fieldName)) {
            continue;
          }
          if (!m_ignoreInvisibleFields || ClassUtils.isFieldVisible(field, aClass)) {
            return aClass;
          }
        }
        aClass = ClassUtils.getContainingClass(aClass);
      }
      return null;
    }
  }
}