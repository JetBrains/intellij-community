/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NestedMethodCallInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreFieldInitializations = true;
  protected boolean ignoreStaticMethods = false;
  protected boolean ignoreGetterCalls = false;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("nested.method.call.ignore.option"), "m_ignoreFieldInitializations");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.calls.to.static.methods"), "ignoreStaticMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.calls.to.property.getters"), "ignoreGetterCalls");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("nested.method.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("nested.method.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedMethodCallVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreStaticMethods) {
      node.addContent(new Element("option").setAttribute("name", "ignoreStaticMethods")
                        .setAttribute("value", String.valueOf(ignoreStaticMethods)));
    }
    if (ignoreGetterCalls) {
      node.addContent(new Element("option").setAttribute("name", "ignoreGetterCalls")
                        .setAttribute("value", String.valueOf(ignoreGetterCalls)));
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      if ("ignoreGetterCalls".equals(option.getAttributeValue("name"))) {
        ignoreGetterCalls = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
      else if ("ignoreStaticMethods".equals(option.getAttributeValue("name"))) {
        ignoreStaticMethods = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class NestedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiExpression outerExpression = expression;
      while (outerExpression.getParent() instanceof PsiExpression) {
        outerExpression = (PsiExpression)outerExpression.getParent();
      }
      final PsiElement parent = outerExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return;
      }
      if (JavaPsiConstructorUtil.isConstructorCall(grandParent)) {
        //ignore nested method calls at the start of a constructor,
        //where they can't be extracted
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (ignoreStaticMethods || ignoreGetterCalls) {
        if (ignoreStaticMethods && method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        if (ignoreGetterCalls && PropertyUtil.isSimpleGetter(method)) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}