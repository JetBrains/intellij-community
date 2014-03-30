/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.UninitializedReadCollector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StaticVariableUninitializedUseInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  @Override
  @NotNull
  public String getID() {
    return "StaticVariableUsedBeforeInitialization";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "static.variable.used.before.initialization.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.variable.used.before.initialization.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "primitive.fields.ignore.option"),
      this, "m_ignorePrimitives");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticVariableInitializationVisitor();
  }

  private class StaticVariableInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (field.getInitializer() != null) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        return;
      }
      if (m_ignorePrimitives) {
        final PsiType type = field.getType();
        if (ClassUtils.isPrimitive(type)) {
          return;
        }
      }
      final PsiClassInitializer[] initializers =
        containingClass.getInitializers();
      // Do the static initializers come in actual order in file?
      // (They need to.)
      final UninitializedReadCollector uninitializedReadCollector =
        new UninitializedReadCollector();
      boolean assigned = false;
      for (final PsiClassInitializer initializer : initializers) {
        if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiCodeBlock body = initializer.getBody();
        if (uninitializedReadCollector.blockAssignsVariable(
          body, field)) {
          assigned = true;
          break;
        }
      }
      if (assigned) {
        final PsiExpression[] badReads =
          uninitializedReadCollector.getUninitializedReads();
        for (PsiExpression badRead : badReads) {
          registerError(badRead);
        }
        return;
      }
      final PsiMethod[] methods = containingClass.getMethods();
      for (PsiMethod method : methods) {
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        final PsiCodeBlock body = method.getBody();
        uninitializedReadCollector.blockAssignsVariable(body, field);
      }
      final PsiExpression[] moreBadReads =
        uninitializedReadCollector.getUninitializedReads();
      for (PsiExpression badRead : moreBadReads) {
        registerError(badRead);
      }
    }
  }
}