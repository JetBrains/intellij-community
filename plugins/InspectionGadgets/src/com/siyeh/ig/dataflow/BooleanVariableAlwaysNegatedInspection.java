/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.invertBoolean.InvertBooleanDialog;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class BooleanVariableAlwaysNegatedInspection extends BooleanVariableAlwaysNegatedInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return new BooleanVariableIsAlwaysNegatedFix(variable.getName());
  }

  private static class BooleanVariableIsAlwaysNegatedFix
    extends InspectionGadgetsFix {

    private final String name;

    public BooleanVariableIsAlwaysNegatedFix(String name) {
      this.name = name;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message(
        "boolean.variable.always.inverted.quickfix", name);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiVariable variable =
        PsiTreeUtil.getParentOfType(element, PsiVariable.class);
      if (variable == null) {
        return;
      }
      final InvertBooleanDialog dialog = new InvertBooleanDialog(variable);
      dialog.show();
    }
  }
}
