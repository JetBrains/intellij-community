/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EqualityToSafeEqualsFix extends InspectionGadgetsFix {

  private final boolean myNegated;

  private EqualityToSafeEqualsFix(boolean negated) {
    myNegated = negated;
  }

  @Nullable
  public static EqualityToSafeEqualsFix buildFix(PsiBinaryExpression expression) {
    final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
    if ((lhs instanceof PsiReferenceExpression)) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if ((target instanceof PsiModifierListOwner)) {
        NullableNotNullManager.getInstance(expression.getProject());
        if (NullableNotNullManager.isNotNull((PsiModifierListOwner)target)) {
          return null;
        }
      }
    }
    return new EqualityToSafeEqualsFix(JavaTokenType.NE.equals(expression.getOperationTokenType()));
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return myNegated
           ? InspectionGadgetsBundle.message("inequality.to.safe.not.equals.quickfix")
           : InspectionGadgetsBundle.message("equality.to.safe.equals.quickfix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("equality.to.safe.equals.quickfix");
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
    if (lhs == null ||  rhs == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String lhsText = tracker.text(lhs);
    final String rhsText = tracker.text(rhs);
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (PsiUtil.isLanguageLevel7OrHigher(expression) && ClassUtils.findClass("java.util.Objects", expression) != null) {
      if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
        newExpression.append('!');
      }
      newExpression.append("java.util.Objects.equals(").append(lhsText).append(',').append(rhsText).append(')');
    }
    else {
      newExpression.append(lhsText).append("==null?").append(rhsText).append(expression.getOperationSign().getText()).append(" null:");
      if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
        newExpression.append('!');
      }
      if (ParenthesesUtils.getPrecedence(lhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
        newExpression.append('(').append(lhsText).append(')');
      }
      else {
        newExpression.append(lhsText);
      }
      newExpression.append(".equals(").append(rhsText).append(')');
    }

    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression.toString(), tracker);
  }
}