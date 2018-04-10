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
package com.siyeh.ig.cloneable;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CloneableImplementsCloneInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCloneableDueToInheritance = false;

  @Override
  @NotNull
  public String getID() {
    return "CloneableClassWithoutClone";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("cloneable.class.without.clone.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("cloneable.class.without.clone.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "cloneable.class.without.clone.ignore.option"), this, "m_ignoreCloneableDueToInheritance");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    final PsiMethod[] superMethods = aClass.findMethodsByName(HardcodedMethodConstants.CLONE, true);
    boolean generateThrows = false;
    for (PsiMethod method : superMethods) {
      if (CloneUtils.isClone(method)) {
        if (method.hasModifierProperty(PsiModifier.FINAL)) {
          return null;
        }
        generateThrows = MethodUtils.hasInThrows(method, "java.lang.CloneNotSupportedException");
        break;
      }
    }
    return new CreateCloneMethodFix(generateThrows);
  }

  private static class CreateCloneMethodFix extends InspectionGadgetsFix {

    private final boolean myGenerateThrows;

    public CreateCloneMethodFix(boolean generateThrows) {
      myGenerateThrows = generateThrows;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("cloneable.class.without.clone.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      final StringBuilder methodText = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(aClass) && CodeStyleSettingsManager.getSettings(aClass.getProject())
        .getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION) {
        methodText.append("@java.lang.Override ");
      }
      methodText.append("public ").append(aClass.getName());
      final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
      if (typeParameterList != null) {
        methodText.append(typeParameterList.getText());
      }
      methodText.append(" clone() ");
      if (myGenerateThrows) {
        methodText.append("throws java.lang.CloneNotSupportedException ");
      }
      methodText.append("{\nreturn (").append(element.getText()).append(") super.clone();\n").append("}");
      final PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText.toString(), element);
      final PsiElement newElement = parent.add(method);
      if (isOnTheFly()) {
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
          GenerateMembersUtil.positionCaret(editor, newElement, true);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneableImplementsCloneVisitor();
  }

  private class CloneableImplementsCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (m_ignoreCloneableDueToInheritance) {
        if (!CloneUtils.isDirectlyCloneable(aClass)) {
          return;
        }
      }
      else if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (CloneUtils.isClone(method)) {
          return;
        }
      }
      registerClassError(aClass, aClass);
    }
  }
}