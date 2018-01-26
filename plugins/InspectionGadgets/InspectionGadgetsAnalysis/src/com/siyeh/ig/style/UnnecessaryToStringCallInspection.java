/*
 * Copyright 2008-2018 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryToStringCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String text = (String)infos[0];
    return InspectionGadgetsBundle.message("unnecessary.tostring.call.problem.descriptor", text);
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String text = (String)infos[0];
    return new UnnecessaryToStringCallFix(text);
  }

  @NonNls
  static String calculateReplacementText(PsiExpression expression) {
    if (expression == null) {
      return "this";
    }
    return expression.getText();
  }

  private static class UnnecessaryToStringCallFix extends InspectionGadgetsFix {

    private final String replacementText;

    private UnnecessaryToStringCallFix(String replacementText) {
      this.replacementText = replacementText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.call.to.string.valueof.quickfix", replacementText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)descriptor.getPsiElement().getParent().getParent();
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        PsiReplacementUtil.replaceExpression(methodCallExpression, "this");
      }
      else {
        methodCallExpression.replace(qualifier);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryToStringCallVisitor();
  }

  private static class UnnecessaryToStringCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (!"toString".equals(referenceName)) {
        return;
      }
      PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (qualifier.getType() instanceof PsiArrayType) {
        // do not warn on nonsensical code
        return;
      }
      if (qualifier instanceof PsiSuperExpression) {
        return;
      }
      final boolean throwable = TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.lang.Throwable");
      if (ExpressionUtils.isConversionToStringNecessary(expression, throwable)) {
        return;
      }
      registerError(referenceNameElement, ProblemHighlightType.LIKE_UNUSED_SYMBOL, calculateReplacementText(qualifier));
    }
  }
}
