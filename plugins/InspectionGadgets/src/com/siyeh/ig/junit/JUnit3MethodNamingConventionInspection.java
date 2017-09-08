/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.naming.ConventionInspection;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class JUnit3MethodNamingConventionInspection extends ConventionInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit3.method.naming.convention.display.name");
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("junit3.method.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "test[A-Za-z_\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return 8;
  }

  @Override
  protected int getDefaultMaxLength() {
    return 64;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new JUnit3MethodNamingConventionVisitor();
  }

  private class JUnit3MethodNamingConventionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!TestUtils.isJUnit3TestMethod(method) || !TestUtils.isRunnable(method)) {
        return;
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
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
