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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.InlineCallFix;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CallToSimpleGetterInClassInspection extends CallToSimpleGetterInClassInspectionBase {
  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.ignore.option"),
                             "ignoreGetterCallsOnOtherObjects");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("call.to.private.simple.getter.in.class.option"),
                             "onlyReportPrivateGetter");
    return optionsPanel;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new InlineOrDeleteCallFix(InspectionGadgetsBundle.message("call.to.simple.getter.in.class.inline.quickfix"));
  }

  private static class InlineOrDeleteCallFix extends InlineCallFix {
    public InlineOrDeleteCallFix(String name) {
      super(name);
    }
  
    @Override
    protected void inline(Project project, PsiReferenceExpression methodExpression, PsiMethod method) {
      final PsiElement statement = methodExpression.getParent().getParent();
      if (statement instanceof PsiExpressionStatement) {
        statement.delete();
      } else {
        super.inline(project, methodExpression, method);
      }
    }
  }
}