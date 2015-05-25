/*
 * Copyright 2009-2015 Bas Leijdekkers
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DynamicRegexReplaceableByCompiledPatternInspection extends DynamicRegexReplaceableByCompiledPatternInspectionBase {
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DynamicRegexReplaceableByCompiledPatternFix();
  }

  private static class DynamicRegexReplaceableByCompiledPatternFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "dynamic.regex.replaceable.by.compiled.pattern.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiClass aClass = ClassUtils.getContainingStaticClass(element);
      if (aClass == null) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      final PsiElement grandParent = methodExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList list = methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = list.getExpressions();
      @NonNls final StringBuilder fieldText =
        new StringBuilder("private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile(");
      final int expressionsLength = expressions.length;
      if (expressionsLength > 0) {
        fieldText.append(expressions[0].getText());
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      final boolean literalReplacement = "replace".equals(methodName);
      if (literalReplacement) {
        fieldText.append(", java.util.regex.Pattern.LITERAL");
      }
      fieldText.append(");");

      @NonNls final StringBuilder expressionText = new StringBuilder("PATTERN.");
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      @NonNls final String qualifierText = (qualifier == null) ? "this" : qualifier.getText();
      if ("split".equals(methodName)) {
        expressionText.append(methodName);
        expressionText.append('(');
        expressionText.append(qualifierText);
        for (int i = 1; i < expressionsLength; i++) {
          expressionText.append(',').append(expressions[i].getText());
        }
        expressionText.append(')');
      }
      else {
        expressionText.append("matcher(").append(qualifierText).append(").");
        if (literalReplacement) {
          expressionText.append("replaceAll");
        }
        else {
          expressionText.append(methodName);
        }
        expressionText.append('(');
        if (literalReplacement) {
          expressionText.append("java.util.regex.Matcher.quoteReplacement(");
        }
        if (expressionsLength > 1) {
          expressionText.append(expressions[1].getText());
          for (int i = 2; i < expressionsLength; i++) {
            expressionText.append(',').append(expressions[i].getText());
          }
        }
        if (literalReplacement) {
          expressionText.append(')');
        }
        expressionText.append(')');
      }

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiField newField = factory.createFieldFromText(fieldText.toString(), element);
      final PsiElement field = aClass.add(newField);
      final PsiExpression newExpression = factory.createExpressionFromText(expressionText.toString(), element);
      PsiMethodCallExpression newMethodCallExpression = (PsiMethodCallExpression)methodCallExpression.replace(newExpression);
      newMethodCallExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newMethodCallExpression);
      final PsiReferenceExpression reference = getReference(newMethodCallExpression);
      HighlightUtils.showRenameTemplate(aClass, (PsiNameIdentifierOwner)field, reference);
    }

    private static PsiReferenceExpression getReference(PsiMethodCallExpression newMethodCallExpression) {
      final PsiReferenceExpression methodExpression = newMethodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)qualifierExpression;
        return getReference(methodCallExpression);
      }
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return null;
      }
      return (PsiReferenceExpression)qualifierExpression;
    }
  }
}