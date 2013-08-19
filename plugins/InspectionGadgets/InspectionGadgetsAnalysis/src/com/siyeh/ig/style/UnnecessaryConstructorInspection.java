/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryConstructorInspection extends BaseInspection {

  @NonNls
  private static final String SUPER_CALL_TEXT = PsiKeyword.SUPER + "();";

  @SuppressWarnings("PublicField")
  public boolean ignoreAnnotations = false;

  @Override
  @NotNull
  public String getID() {
    return "RedundantNoArgConstructor";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.constructor.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "unnecessary.constructor.annotation.option"),
      this, "ignoreAnnotations");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryConstructorFix();
  }

  private static class UnnecessaryConstructorFix
    extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.constructor.remove.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement nameIdentifier = descriptor.getPsiElement();
      final PsiElement constructor = nameIdentifier.getParent();
      assert constructor != null;
      deleteElement(constructor);
    }
  }

  private class UnnecessaryConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length != 1) {
        return;
      }
      final PsiMethod constructor = constructors[0];
      if (!constructor.hasModifierProperty(PsiModifier.PRIVATE) &&
          aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
          aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PROTECTED) &&
          aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (!constructor.hasModifierProperty(PsiModifier.PUBLIC) &&
          aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      if (ignoreAnnotations) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        final PsiAnnotation[] annotations =
          modifierList.getAnnotations();
        if (annotations.length > 0) {
          return;
        }
      }
      final PsiReferenceList throwsList = constructor.getThrowsList();
      final PsiJavaCodeReferenceElement[] elements =
        throwsList.getReferenceElements();
      if (elements.length != 0) {
        return;
      }
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        registerMethodError(constructor);
      }
      else if (statements.length == 1) {
        final PsiStatement statement = statements[0];
        if (SUPER_CALL_TEXT.equals(statement.getText())) {
          registerMethodError(constructor);
        }
      }
    }
  }
}