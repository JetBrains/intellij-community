/*
 * Copyright 2009-2018 Bas Leijdekkers
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
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;

public class DynamicRegexReplaceableByCompiledPatternInspection extends BaseInspection {
  @NonNls
  protected static final Collection<String> regexMethodNames = Set.of(
    "matches", "replace", "replaceFirst", "replaceAll", "split"
  );

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DynamicRegexReplaceableByCompiledPatternFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "dynamic.regex.replaceable.by.compiled.pattern.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DynamicRegexReplaceableByCompiledPatternVisitor();
  }

  private static class DynamicRegexReplaceableByCompiledPatternFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
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
      CommentTracker commentTracker = new CommentTracker();
      @NonNls final StringBuilder fieldText =
        new StringBuilder("private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile(");
      final int expressionsLength = expressions.length;
      if (expressionsLength > 0) {
        fieldText.append(commentTracker.text(expressions[0]));
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      final boolean literalReplacement = "replace".equals(methodName);
      if (literalReplacement) {
        fieldText.append(", java.util.regex.Pattern.LITERAL");
      }
      fieldText.append(");");

      @NonNls final StringBuilder expressionText = new StringBuilder("PATTERN.");
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      @NonNls final String qualifierText = (qualifier == null) ? "this" : commentTracker.text(qualifier);
      if ("split".equals(methodName)) {
        expressionText.append(methodName);
        expressionText.append('(');
        expressionText.append(qualifierText);
        for (int i = 1; i < expressionsLength; i++) {
          expressionText.append(',').append(commentTracker.text(expressions[i]));
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
        boolean quote = false;
        if (literalReplacement) {
          quote = (expressionsLength > 1 && needsQuote(expressions[1]));
          if (quote) {
            expressionText.append("java.util.regex.Matcher.quoteReplacement(");
          }
        }
        if (expressionsLength > 1) {
          expressionText.append(commentTracker.text(expressions[1]));
          for (int i = 2; i < expressionsLength; i++) {
            expressionText.append(',').append(commentTracker.text(expressions[i]));
          }
        }
        if (literalReplacement && quote) {
          expressionText.append(')');
        }
        expressionText.append(')');
      }

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiElement field = aClass.add(factory.createFieldFromText(fieldText.toString(), element));
      PsiMethodCallExpression newMethodCallExpression =
        (PsiMethodCallExpression)commentTracker.replaceAndRestoreComments(methodCallExpression, expressionText.toString());
      newMethodCallExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newMethodCallExpression);
      if (newMethodCallExpression == null) return;
      final PsiReferenceExpression reference = getReference(newMethodCallExpression);
      HighlightUtils.showRenameTemplate(aClass, (PsiNameIdentifierOwner)field, reference);
    }

    private static boolean needsQuote(PsiExpression expr) {
      Object constExprValue = ExpressionUtils.computeConstantExpression(expr);
      return !(constExprValue instanceof String) ||
             Matcher.quoteReplacement((String) constExprValue) != constExprValue;
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

  private static class DynamicRegexReplaceableByCompiledPatternVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isCallToRegexMethod(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!regexMethodNames.contains(name)) {
        return false;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return false;
      }
      final Object value = ExpressionUtils.computeConstantExpression(arguments[0]);
      if (!(value instanceof String)) {
        return false;
      }
      final String regex = (String)value;
      if (PsiUtil.isLanguageLevel7OrHigher(expression) && "split".equals(name) && isOptimizedPattern(regex) ||
          PsiUtil.isLanguageLevel9OrHigher(expression) && "replace".equals(name)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final String className = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
        return false;
      }
      return AllowedApiFilterExtension.isClassAllowed("java.util.regex.Pattern", expression);
    }

    private static boolean isOptimizedPattern(String regex) {
      // from String.split()
      int ch;
      return ((regex.length() == 1 &&
               ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) ||
              (regex.length() == 2 &&
               regex.charAt(0) == '\\' &&
               (((ch = regex.charAt(1))-'0')|('9'-ch)) < 0 &&
               ((ch-'a')|('z'-ch)) < 0 &&
               ((ch-'A')|('Z'-ch)) < 0)) &&
             (ch < Character.MIN_HIGH_SURROGATE ||
              ch > Character.MAX_LOW_SURROGATE);
    }
  }
}