/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ClassWithoutNoArgConstructorInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreClassesWithNoConstructors = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.without.no.arg.constructor.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "class.without.no.arg.constructor.ignore.option"),
      this, "m_ignoreClassesWithNoConstructors");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.without.no.arg.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutNoArgConstructorVisitor();
  }

  private class ClassWithoutNoArgConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (m_ignoreClassesWithNoConstructors &&
          !classHasConstructor(aClass)) {
        return;
      }
      if (classHasNoArgConstructor(aClass)) {
        return;
      }
      registerClassError(aClass);
    }

    private boolean classHasNoArgConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        final PsiParameterList parameterList =
          constructor.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return true;
        }
      }
      return false;
    }

    private boolean classHasConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      return constructors.length != 0;
    }
  }
}