/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StaticVariableNamingConventionInspection extends
                                                      ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;
  @SuppressWarnings({"PublicField"})
  public boolean checkMutableFinals = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("static.variable.naming.convention.display.name");
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("static.variable.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "s_[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @NotNull
  @Override
  public JComponent[] createExtraOptions() {
    return new JComponent[]{
      new CheckBox(InspectionGadgetsBundle.message("static.variable.naming.convention.mutable.option"), this, "checkMutableFinals")
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (!checkMutableFinals) {
          return;
        }
        else {
          final PsiType type = field.getType();
          if (ClassUtils.isImmutable(type)) {
            return;
          }
        }
      }
      final String name = field.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerFieldError(field, name);
    }
  }
}