/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspection extends UnnecessaryInitCauseInspectionBase implements CleanupLocalInspectionTool {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryInitCauseFix();
  }

  private static class UnnecessaryInitCauseFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.initcause.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression argument = ExpressionUtils.getFirstExpressionInList(argumentList);
      if (argument == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      final PsiNewExpression newExpression = findNewExpression(qualifier);
      if (newExpression == null) {
        return;
      }
      final PsiExpressionList argumentList1 = newExpression.getArgumentList();
      if (argumentList1 == null) {
        return;
      }
      final PsiElement newElement = argumentList1.add(argument);
      if (isOnTheFly()) {
        HighlightUtils.highlightElement(newElement);
      }
      final PsiElement parent = methodCallExpression.getParent();
      if (parent instanceof PsiExpressionStatement) {
        parent.delete();
      }
      else {
        methodCallExpression.replace(qualifier);
      }
    }
  }
}
