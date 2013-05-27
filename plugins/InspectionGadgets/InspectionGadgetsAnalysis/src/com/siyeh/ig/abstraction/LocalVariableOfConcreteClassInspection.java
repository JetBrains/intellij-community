/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class LocalVariableOfConcreteClassInspection
  extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAbstractClasses = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "local.variable.of.concrete.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... arg) {
    final PsiNamedElement variable = (PsiNamedElement)arg[0];
    final String name = variable.getName();
    return InspectionGadgetsBundle.message(
      "local.variable.of.concrete.class.problem.descriptor", name);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "local.variable.of.concrete.class.option"),
      this, "ignoreAbstractClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LocalVariableOfConcreteClassVisitor();
  }

  private class LocalVariableOfConcreteClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(
      @NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (!ConcreteClassUtil.typeIsConcreteClass(typeElement,
                                                 ignoreAbstractClasses)) {
        return;
      }
      registerError(typeElement, variable);
    }
  }
}