/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.finalization;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FinalizeCallsSuperFinalizeInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreObjectSubclasses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreTrivialFinalizers = true;

  @Override
  @NotNull
  public String getID() {
    return "FinalizeDoesntCallSuperFinalize";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "finalize.doesnt.call.super.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.doesnt.call.super.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "finalize.doesnt.call.super.ignore.option"),
                             "ignoreObjectSubclasses");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "ignore.trivial.finalizers.option"),
                             "ignoreTrivialFinalizers");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NoExplicitFinalizeCallsVisitor();
  }

  private class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super;
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.FINALIZE.equals(methodName)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.NATIVE) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (ignoreObjectSubclasses) {
        final PsiClass superClass = containingClass.getSuperClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClassName)) {
            return;
          }
        }
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      if (MethodCallUtils.containsSuperMethodCall(HardcodedMethodConstants.FINALIZE, method)) {
        return;
      }
      if (ignoreTrivialFinalizers && MethodUtils.isTrivial(method, false)) {
        return;
      }
      registerMethodError(method);
    }
  }
}