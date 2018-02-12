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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RandomDoubleForRandomIntegerInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "UsingRandomNextDoubleForRandomInteger";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "random.double.for.random.integer.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "random.double.for.random.integer.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RandomDoubleForRandomIntegerFix();
  }

  private static class RandomDoubleForRandomIntegerFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "random.double.for.random.integer.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiIdentifier name = (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression expression =
        (PsiReferenceExpression)name.getParent();
      if (expression == null) {
        return;
      }
      final PsiExpression call = (PsiExpression)expression.getParent();
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiBinaryExpression multiplication = (PsiBinaryExpression)getContainingExpression(call);
      if (multiplication == null) {
        return;
      }
      final PsiExpression cast = getContainingExpression(multiplication);
      if (cast == null) {
        return;
      }
      final PsiExpression lhs = multiplication.getLOperand();
      final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
      final PsiExpression multiplierExpression = call.equals(strippedLhs) ? multiplication.getROperand() : lhs;
      assert multiplierExpression != null;
      CommentTracker commentTracker = new CommentTracker();
      final String multiplierText = commentTracker.text(multiplierExpression);
      PsiReplacementUtil.replaceExpression(cast, commentTracker.text(qualifier) + ".nextInt((int) " + multiplierText + ')', commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RandomDoubleForRandomIntegerVisitor();
  }

  private static class RandomDoubleForRandomIntegerVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String nextDouble = "nextDouble";
      if (!nextDouble.equals(methodName)) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.util.Random".equals(className)) {
        return;
      }
      final PsiExpression possibleMultiplierExpression =
        getContainingExpression(call);
      if (!isMultiplier(possibleMultiplierExpression)) {
        return;
      }
      final PsiExpression possibleIntCastExpression =
        getContainingExpression(possibleMultiplierExpression);
      if (!isIntCast(possibleIntCastExpression)) {
        return;
      }
      registerMethodCallError(call);
    }

    private static boolean isMultiplier(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)expression;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      return JavaTokenType.ASTERISK.equals(tokenType);
    }

    private static boolean isIntCast(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiTypeCastExpression)) {
        return false;
      }
      final PsiTypeCastExpression castExpression =
        (PsiTypeCastExpression)expression;
      final PsiType type = castExpression.getType();
      return PsiType.INT.equals(type);
    }
  }

  @Nullable
  static PsiExpression getContainingExpression(PsiExpression expression) {
    PsiElement ancestor = expression.getParent();
    while (true) {
      if (ancestor == null) {
        return null;
      }
      if (!(ancestor instanceof PsiExpression)) {
        return null;
      }
      if (!(ancestor instanceof PsiParenthesizedExpression)) {
        return (PsiExpression)ancestor;
      }
      ancestor = ancestor.getParent();
    }
  }
}