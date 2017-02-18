/*
 * Copyright 2006-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DuplicateBooleanBranchInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("duplicate.boolean.branch.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("duplicate.boolean.branch.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DuplicateBooleanBranchVisitor();
  }

  private static class DuplicateBooleanBranchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ANDAND) && !tokenType.equals(JavaTokenType.OROR)) {
        return;
      }
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression parentExpression = (PsiBinaryExpression)parent;
        if (tokenType.equals(parentExpression.getOperationTokenType())) {
          return;
        }
      }
      final Set<PsiExpression> conditions = new HashSet<>();
      collectConditions(expression, conditions, tokenType);
      final int numConditions = conditions.size();
      if (numConditions < 2) {
        return;
      }
      final PsiExpression[] conditionArray = conditions.toArray(new PsiExpression[numConditions]);
      final boolean[] matched = new boolean[conditionArray.length];
      Arrays.fill(matched, false);
      for (int i = 0; i < conditionArray.length; i++) {
        if (matched[i]) {
          continue;
        }
        final PsiExpression condition = conditionArray[i];
        for (int j = i + 1; j < conditionArray.length; j++) {
          if (matched[j]) {
            continue;
          }
          final PsiExpression testCondition = conditionArray[j];
          final boolean areEquivalent = EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(condition, testCondition);
          if (areEquivalent) {
            registerError(testCondition);
            if (!matched[i]) {
              registerError(condition);
            }
            matched[i] = true;
            matched[j] = true;
          }
        }
      }
    }

    private static void collectConditions(PsiExpression condition, Set<PsiExpression> conditions, IElementType tokenType) {
      if (condition == null) {
        return;
      }
      if (condition instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)condition;
        final PsiExpression contents = parenthesizedExpression.getExpression();
        collectConditions(contents, conditions, tokenType);
        return;
      }
      if (condition instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
        final IElementType testTokeType = polyadicExpression.getOperationTokenType();
        if (testTokeType.equals(tokenType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            collectConditions(operand, conditions, tokenType);
          }
          return;
        }
      }
      conditions.add(condition);
    }
  }
}