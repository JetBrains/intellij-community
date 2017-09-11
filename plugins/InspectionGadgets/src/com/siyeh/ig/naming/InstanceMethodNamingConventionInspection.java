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
package com.siyeh.ig.naming;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class InstanceMethodNamingConventionInspection extends
                                                      ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instance.method.naming.convention.display.name");
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("instance.method.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
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

  private static boolean isTestNGTestMethod(PsiMethod method) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if ("TestNG".equals(framework.getName())) {
        return framework.isTestMethod(method);
      }
    }
    return false;
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.NATIVE) && isInspectionEnabled("NativeMethodNamingConvention", method)) {
        return;
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      if (TestUtils.isRunnable(method)) {
        if (TestUtils.isAnnotatedTestMethod(method) && isInspectionEnabled("JUnit4MethodNamingConvention", method)) {
          return;
        }
        if (TestUtils.isJUnit3TestMethod(method) && isInspectionEnabled("JUnit3MethodNamingConvention", method)) {
          return;
        }
      }
      if (isTestNGTestMethod(method) && isInspectionEnabled("TestNGMethodNamingConvention", method)) {
        return;
      }
      final String name = method.getName();
      if (isValid(name)) {
        return;
      }
      if (!isOnTheFly() && MethodUtils.hasSuper(method)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method, name);
    }
  }
}