/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ParameterNameDiffersFromOverriddenParameterInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreSingleCharacterNames = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreOverridesOfLibraryMethods = false;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "parameter.name.differs.from.overridden.parameter.ignore.character.option"),
                             "m_ignoreSingleCharacterNames");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "parameter.name.differs.from.overridden.parameter.ignore.library.option"),
                             "m_ignoreOverridesOfLibraryMethods");
    return optionsPanel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix((String)infos[0], false, false);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "parameter.name.differs.from.overridden.parameter.problem.descriptor",
      infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterNameDiffersFromOverriddenParameterVisitor();
  }

  private class ParameterNameDiffersFromOverriddenParameterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.isEmpty()) {
        return;
      }
      final PsiMethod superMethod = getSuperMethod(method);
      if (superMethod == null) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      checkParameters(superMethod, parameters);
    }

    @Nullable
    private PsiMethod getSuperMethod(@NotNull PsiMethod method) {
      if (method.isConstructor()) {
        PsiMethod superCtor = JavaPsiConstructorUtil.findConstructorInSuper(method);
        if (superCtor == null) return null;
        PsiClass superClass = superCtor.getContainingClass();
        if (superClass == null || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()) ||
            superCtor.getParameters().length != method.getParameters().length) {
          return null;
        }
        return superCtor;
      }
      return MethodUtils.getSuper(method);
    }

    private void checkParameters(@NotNull PsiMethod superMethod, PsiParameter[] parameters) {
      if (m_ignoreOverridesOfLibraryMethods) {
        final PsiClass containingClass =
          superMethod.getContainingClass();
        if (containingClass != null &&
            LibraryUtil.classIsInLibrary(containingClass)) {
          return;
        }
      }
      final PsiParameterList superParameterList =
        superMethod.getParameterList();
      final PsiParameter[] superParameters =
        superParameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final String parameterName = parameter.getName();
        final String superParameterName = superParameters[i].getName();
        if (superParameterName.equals(parameterName)) {
          continue;
        }
        final PsiType parameterType = parameter.getType();
        final PsiType superParameterType = superParameters[i].getType();
        if (!parameterType.equalsToText(superParameterType.getCanonicalText())) {
          continue;
        }
        if (m_ignoreSingleCharacterNames &&
            superParameterName.length() == 1) {
          continue;
        }
        registerVariableError(parameter, superParameterName);
      }
    }
  }
}