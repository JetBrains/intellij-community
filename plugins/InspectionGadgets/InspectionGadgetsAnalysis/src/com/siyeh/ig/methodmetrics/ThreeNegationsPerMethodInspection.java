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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThreeNegationsPerMethodInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreInEquals = true;

  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreInAssert = false;

  @Override
  @NotNull
  public String getID() {
    return "MethodWithMoreThanThreeNegations";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "three.negations.per.method.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("three.negations.per.method.ignore.option"), "m_ignoreInEquals");
    panel.addCheckbox(InspectionGadgetsBundle.message("three.negations.per.method.ignore.assert.option"), "ignoreInAssert");
    return panel;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer negationCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "three.negations.per.method.problem.descriptor", negationCount);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreeNegationsPerMethodVisitor();
  }

  private class ThreeNegationsPerMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final NegationCountVisitor visitor = new NegationCountVisitor(ignoreInAssert);
      method.accept(visitor);
      final int negationCount = visitor.getCount();
      if (negationCount <= 3) {
        return;
      }
      if (m_ignoreInEquals && MethodUtils.isEquals(method)) {
        return;
      }
      registerMethodError(method, Integer.valueOf(negationCount));
    }
  }
}