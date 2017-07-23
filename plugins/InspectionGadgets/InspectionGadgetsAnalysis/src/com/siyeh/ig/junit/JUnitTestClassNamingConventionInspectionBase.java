/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.naming.ConventionInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class JUnitTestClassNamingConventionInspectionBase extends ConventionInspection {
  private static final int DEFAULT_MIN_LENGTH = 8;
  private static final int DEFAULT_MAX_LENGTH = 64;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "junit.test.class.naming.convention.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("junit.test.class.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "[A-Z][A-Za-z\\d]*Test";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {
    @Override
    public void visitElement(PsiElement element) {
      if (!(element instanceof PsiClass)) {
        super.visitElement(element);
        return;
      }

      final PsiClass aClass = (PsiClass)element;
      if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }

      final Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(aClass);
      if (frameworks.stream().noneMatch(framework -> framework.getName().startsWith("JUnit") && framework.isTestClass(aClass))) {
        return;
      }

      final String name = aClass.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      final PsiIdentifier identifier = aClass.getNameIdentifier();
      if (identifier == null) {
        return;
      }
      if (!identifier.isPhysical()) {
        final PsiElement navigationElement = identifier.getNavigationElement();
        registerError(navigationElement, name);
      }
      else {
        registerClassError(aClass, name);
      }
    }
  }
}
