/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StaticMethodNamingConventionInspectionBase extends ConventionInspection {
  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @SuppressWarnings("PublicField")
  public boolean ignoreNativeMethods = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "static.method.naming.convention.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    if (methodName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message(
        "static.method.naming.convention.problem.descriptor.short");
    }
    else if (methodName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message(
        "static.method.naming.convention.problem.descriptor.long");
    }
    return InspectionGadgetsBundle.message(
      "static.method.naming.convention.problem.descriptor.regex.mismatch",
      getRegex());
  }

  @Override
  public JComponent[] createExtraOptions() {
    return new JComponent[]{
      new CheckBox("ignore 'native' methods", this, "ignoreNativeMethods")
    };
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (ignoreNativeMethods && method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      final String name = method.getName();
      if (isValid(name)) {
        return;
      }
      registerMethodError(method, name);
    }
  }
}
