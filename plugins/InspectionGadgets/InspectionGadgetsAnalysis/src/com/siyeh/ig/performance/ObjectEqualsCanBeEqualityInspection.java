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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ObjectEqualsCanBeEqualityInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("object.equals.can.be.equality.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean negated = (Boolean)infos[0];
    return negated.booleanValue()
           ? InspectionGadgetsBundle.message("object.equals.can.be.equality.problem.descriptor")
           : InspectionGadgetsBundle.message("not.object.equals.can.be.equality.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean not = (Boolean)infos[0];
    return new EqualsToEqualityFix(not.booleanValue());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualsMayBeEqualityVisitor();
  }

  private static class ObjectEqualsMayBeEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isEqualsCall(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
      if (expressions.length != 1) {
        return;
      }
      final PsiExpression argument = expressions[0];
      if (!TypeConversionUtil.isBinaryOperatorApplicable(JavaTokenType.EQEQ, qualifier, argument, false)) {
        // replacing with == or != will generate uncompilable code
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
      final ProblemHighlightType highlightType = ClassUtils.isFinalClassWithDefaultEquals(aClass)
                                                 ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                                 : ProblemHighlightType.INFORMATION;
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      final boolean negated = parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
      final PsiElement nameToken = methodExpression.getReferenceNameElement();
      assert nameToken != null;
      registerError(nameToken, highlightType, Boolean.valueOf(negated));
    }
  }
}
