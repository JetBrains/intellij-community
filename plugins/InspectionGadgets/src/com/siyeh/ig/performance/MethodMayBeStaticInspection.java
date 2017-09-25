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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.Settings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MethodMayBeStaticInspection extends MethodMayBeStaticInspectionBase {
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiMethod element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        if (element != null) {
          new MakeMethodStaticProcessor(project, element, new Settings(m_replaceQualifier, null, null)).run();
        }
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("change.modifier.quickfix", PsiModifier.STATIC);
      }
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("method.may.be.static.only.option"), ONLY_PRIVATE_OR_FINAL_ATTR_NAME);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("method.may.be.static.empty.option"), IGNORE_EMPTY_METHODS_ATTR_NAME);
    optionsPanel.addCheckbox("Ignore 'default' methods", IGNORE_DEFAULT_METHODS_ATTR_NAME);
    optionsPanel.addCheckbox("Quick fix replaces instance qualifiers with class references", REPLACE_QUALIFIER_ATTR_NAME);
    return optionsPanel;
  }
}
