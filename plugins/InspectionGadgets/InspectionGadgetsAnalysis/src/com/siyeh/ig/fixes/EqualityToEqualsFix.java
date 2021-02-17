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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EqualityToEqualsFix extends InspectionGadgetsFix {

  private final boolean myNegated;

  /**
   * @deprecated use {@link #buildFix(PsiBinaryExpression)} instead
   */
  @Deprecated
  public EqualityToEqualsFix() {
    this(true);
  }

  private EqualityToEqualsFix(boolean negated) {
    myNegated = negated;
  }

  @Nullable
  public static EqualityToEqualsFix buildFix(PsiBinaryExpression expression) {
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final Nullability nullability = NullabilityUtil.getExpressionNullability(expression.getLOperand(), true);
    if (nullability == Nullability.NULLABLE) return null;
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

  public static InspectionGadgetsFix @NotNull [] buildEqualityFixes(PsiBinaryExpression expression) {
    final List<InspectionGadgetsFix> result = new ArrayList<>(2);
    ContainerUtil.addIfNotNull(result, buildFix(expression));
    ContainerUtil.addIfNotNull(result, EqualityToSafeEqualsFix.buildFix(expression));
    return result.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return getFixName(myNegated);
  }

  @NotNull
  public static @IntentionFamilyName String getFixName(boolean negated) {
    return negated
           ? CommonQuickFixBundle.message("fix.replace.x.with.y", "!=", "!equals()")
           : CommonQuickFixBundle.message("fix.replace.x.with.y", "==", "equals()");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getFixName(false);
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement comparisonToken = descriptor.getPsiElement();
    final PsiElement parent = comparisonToken.getParent();
    if (!(parent instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)parent;
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (lhs == null || rhs == null) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (JavaTokenType.NE.equals(expression.getOperationTokenType())) {
      newExpression.append('!');
    }
    newExpression.append(commentTracker.text(lhs, ParenthesesUtils.METHOD_CALL_PRECEDENCE));
    newExpression.append(".equals(").append(commentTracker.text(rhs)).append(')');

    PsiReplacementUtil.replaceExpressionAndShorten(expression, newExpression.toString(), commentTracker);
  }
}