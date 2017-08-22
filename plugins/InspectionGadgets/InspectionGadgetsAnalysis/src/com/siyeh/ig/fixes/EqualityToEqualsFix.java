/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EqualityToEqualsFix extends InspectionGadgetsFix {

  private final boolean myNegated;

  private EqualityToEqualsFix(boolean negated) {
    myNegated = negated;
  }

  @Nullable
  public static EqualityToEqualsFix buildFix(PsiBinaryExpression expression) {
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
    if (lhs instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiModifierListOwner) {
        NullableNotNullManager.getInstance(expression.getProject());
        if (NullableNotNullManager.isNullable((PsiModifierListOwner)target)) {
          return null;
        }
      }
    }
    return new EqualityToEqualsFix(JavaTokenType.NE.equals(expression.getOperationTokenType()));
  }

  @NotNull
  public static InspectionGadgetsFix[] buildEqualityFixes(PsiBinaryExpression expression) {
    final List<InspectionGadgetsFix> result = new ArrayList<>(2);
    ContainerUtil.addIfNotNull(result, buildFix(expression));
    ContainerUtil.addIfNotNull(result, EqualityToSafeEqualsFix.buildFix(expression));
    return result.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return myNegated
           ? InspectionGadgetsBundle.message("inequality.to.not.equals.quickfix")
           : InspectionGadgetsBundle.message("equality.to.equals.quickfix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("equality.to.equals.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement comparisonToken = descriptor.getPsiElement();
    final PsiElement parent = comparisonToken.getParent();
    if (!(parent instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)parent;
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
    final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
    if (lhs == null || rhs == null) {
      return;
    }
    final StringBuilder newExpression = new StringBuilder();
    if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
      newExpression.append('!');
    }
    if (ParenthesesUtils.getPrecedence(lhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      newExpression.append('(').append(lhs.getText()).append(')');
    }
    else {
      newExpression.append(lhs.getText());
    }
    newExpression.append(".equals(").append(rhs.getText()).append(')');
    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression.toString());
  }
}