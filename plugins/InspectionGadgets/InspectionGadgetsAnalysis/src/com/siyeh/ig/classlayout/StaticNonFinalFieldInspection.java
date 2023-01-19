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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class StaticNonFinalFieldInspection extends BaseInspection {

  public boolean ignoreNonPublicFields = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.non.final.field.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return MakeFieldFinalFix.buildFix(field);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonPublicFields", InspectionGadgetsBundle.message("static.non.final.field.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticNonFinalFieldVisitor();
  }

  private class StaticNonFinalFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (ignoreNonPublicFields && !field.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}