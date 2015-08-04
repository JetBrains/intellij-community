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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EqualityToEqualsFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "object.comparison.replace.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement comparisonToken = descriptor.getPsiElement();
    final PsiElement parent = comparisonToken.getParent();
    if (!(parent instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)parent;
    boolean negated = false;
    final IElementType tokenType = expression.getOperationTokenType();
    if (JavaTokenType.NE.equals(tokenType)) {
      negated = true;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression rhs = expression.getROperand();
    final PsiExpression strippedRhs =
      ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    @NonNls final String expString;
    if (PsiUtil.isLanguageLevel7OrHigher(expression)) {
      expString = "java.util.Objects.equals(" + strippedLhs.getText() + ',' + strippedRhs.getText() + ')';
    }
    else if (ParenthesesUtils.getPrecedence(strippedLhs) >
        ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      expString = '(' + strippedLhs.getText() + ").equals(" +
                  strippedRhs.getText() + ')';
    }
    else {
      expString = strippedLhs.getText() + ".equals(" +
                  strippedRhs.getText() + ')';
    }
    @NonNls final String newExpression;
    if (negated) {
      newExpression = '!' + expString;
    }
    else {
      newExpression = expString;
    }
    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression);
  }
}