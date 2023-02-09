/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class ConvertOctalLiteralsToDecimalsFix extends InspectionGadgetsFix {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.octal.literals.to.decimal.literals.quickfix");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiArrayInitializerExpression arrayInitializerExpression)) {
      return;
    }
    for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
      initializer = PsiUtil.skipParenthesizedExprDown(initializer);
      if (!(initializer instanceof PsiLiteralExpression literal)) {
        continue;
      }
      if (!ExpressionUtils.isOctalLiteral(literal)) {
        continue;
      }
      ConvertOctalLiteralToDecimalFix.replaceWithDecimalLiteral(literal);
    }
  }
}
