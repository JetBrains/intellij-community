/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ClassWithoutNoArgConstructorInspection extends ClassInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreClassesWithNoConstructors = true;

  public String getGroupDisplayName() {
    return GroupNames.JAVABEANS_GROUP_NAME;
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("class.without.no.arg.constructor.ignore.option"),
                                          this,
                                          "m_ignoreClassesWithNoConstructors");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutNoArgConstructorVisitor();
  }

  private class ClassWithoutNoArgConstructorVisitor
    extends BaseInspectionVisitor {

    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiAnonymousClass) {
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
  }

  private static boolean classHasNoArgConstructor(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (final PsiMethod constructor : constructors) {
      final PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList != null) {
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters != null && parameters.length == 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean classHasConstructor(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    return constructors.length != 0;
  }
}
