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

class RemoveLeadingZeroFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("remove.leading.zero.to.make.decimal.quickfix");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiLiteralExpression)) {
      return;
    }
    final PsiLiteralExpression literal = (PsiLiteralExpression)element;
    removeLeadingZeroes(literal);
  }

  static void removeLeadingZeroes(PsiLiteralExpression literal) {
    final String text = literal.getText();
    final int max = text.length() - (PsiType.LONG.equals(literal.getType()) ? 2 : 1);
    if (max < 1) {
      return;
    }
    int index = 0;
    while (index < max && (text.charAt(index) == '0' || text.charAt(index) == '_')) {
      index++;
    }
    final String textWithoutLeadingZeros = text.substring(index);
    PsiReplacementUtil.replaceExpression(literal, textWithoutLeadingZeros);
  }
}
