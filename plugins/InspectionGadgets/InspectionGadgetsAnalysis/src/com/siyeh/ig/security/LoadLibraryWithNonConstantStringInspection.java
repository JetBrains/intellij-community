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
package com.siyeh.ig.security;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LoadLibraryWithNonConstantStringInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean considerStaticFinalConstant = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("load.library.with.non.constant.string.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String qualifier = (String)infos[0];
    return InspectionGadgetsBundle.message("load.library.with.non.constant.string.problem.descriptor", qualifier);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("consider.static.final.fields.constant.option"),
                                          this, "considerStaticFinalConstant");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RuntimeExecVisitor();
  }

  private class RuntimeExecVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (MethodCallUtils.callWithNonConstantString(expression, considerStaticFinalConstant,
                                                    "java.lang.System", "load", "loadLibrary")) {
        registerMethodCallError(expression, "System");
      }
      else if (MethodCallUtils.callWithNonConstantString(expression, considerStaticFinalConstant,
                                                         "java.lang.Runtime", "load", "loadLibrary")) {
        registerMethodCallError(expression, "Runtime");
      }
    }
  }
}