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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ChainedMethodCallInspection extends ChainedMethodCallInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("chained.method.call.ignore.option"), "m_ignoreFieldInitializations");
    panel.addCheckbox(InspectionGadgetsBundle.message("chained.method.call.ignore.this.super.option"), "m_ignoreThisSuperCalls");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false) {
      @Nullable
      @Override
      public PsiExpression getExpressionToExtract(PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
          return null;
        }
        final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
        return methodExpression.getQualifierExpression();
      }
    };
  }
}