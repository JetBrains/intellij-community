/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class AutoCloseableResourceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreFromMethodCall = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("auto.closeable.resource.display.name");
  }

  @NotNull
  @Override
  public String getID() {
    return "resource"; // matches Eclipse inspection
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("auto.closeable.resource.problem.descriptor", text);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("auto.closeable.resource.returned.option"),
                                          this, "ignoreFromMethodCall");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoCloseableResourceVisitor();
  }

  private class AutoCloseableResourceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (ignoreFromMethodCall) {
        return;
      }
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (!PsiUtil.isLanguageLevel7OrHigher(expression) || !TypeUtils.expressionHasTypeOrSubtype(expression, "java.lang.AutoCloseable")) {
        return;
      }
      final PsiVariable variable = ResourceInspection.getVariable(expression);
      if (variable instanceof PsiResourceVariable) {
        return;
      }
      if (ResourceInspection.isResourceEscapingFromMethod(variable, expression)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}
