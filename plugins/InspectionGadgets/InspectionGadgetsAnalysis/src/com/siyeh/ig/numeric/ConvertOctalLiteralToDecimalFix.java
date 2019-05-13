/*
 * Copyright 2010-2017 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

class ConvertOctalLiteralToDecimalFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.octal.literal.to.decimal.literal.quickfix");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiLiteralExpression)) {
      return;
    }
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
    replaceWithDecimalLiteral(literalExpression);
  }

  static void replaceWithDecimalLiteral(PsiLiteralExpression literalExpression) {
    final Object value = literalExpression.getValue();
    if (value == null) {
      return;
    }
    final String decimalText = value + (PsiType.LONG.equals(literalExpression.getType()) ? "L" : "");
    PsiReplacementUtil.replaceExpression(literalExpression, decimalText);
  }
}
