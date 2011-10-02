/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class FeatureEnvyInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreTestCases = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("feature.envy.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String className = element.getName();
    return InspectionGadgetsBundle.message(
      "feature.envy.problem.descriptor", className);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "feature.envy.ignore.test.cases.option"), this,
      "ignoreTestCases");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FeatureEnvyVisitor();
  }

  private class FeatureEnvyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (ignoreTestCases) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null &&
            TestFrameworks.getInstance().isTestClass(containingClass)) {
          return;
        }
        if (TestUtils.isJUnitTestMethod(method)) {
          return;
        }
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      final ClassAccessVisitor visitor =
        new ClassAccessVisitor(containingClass);
      method.accept(visitor);
      final Set<PsiClass> overaccessedClasses =
        visitor.getOveraccessedClasses();
      for (PsiClass aClass : overaccessedClasses) {
        registerError(nameIdentifier, aClass);
      }
    }
  }
}