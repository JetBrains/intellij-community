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
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWithArraysAsListIntention extends Intention implements HighPriorityAction {

  private String replacementText = null;

  @NotNull
  @Override
  public String getText() {
    return IntentionPowerPackBundle.message("replace.with.arrays.as.list.intention.name", replacementText);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return e -> {
      if (!(e instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)e;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals("java.util.Collections")) {
        return false;
      }
      final String name = method.getName();
      return (replacementText = getReplacementMethodText(name, methodCallExpression)) != null;
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
    if (parameterList != null) {
      final int dotIndex = replacementText.lastIndexOf('.') + 1;
      replacementText = replacementText.substring(0, dotIndex) + parameterList.getText() + replacementText.substring(dotIndex);
    }
    PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, replacementText + argumentList.getText());
  }

  private static String getReplacementMethodText(String methodName, PsiMethodCallExpression context) {
    final PsiExpression[] arguments = context.getArgumentList().getExpressions();
    if (methodName.equals("emptyList") && arguments.length == 1 &&
        !PsiUtil.isLanguageLevel9OrHigher(context) && ClassUtils.findClass("com.google.common.collect.ImmutableList", context) == null) {
      return "java.util.Collections.singletonList";
    }
    if (methodName.equals("emptyList") || methodName.equals("singletonList")) {
      if (Arrays.stream(arguments).noneMatch(e -> isPossiblyNull(e))) {
        if (PsiUtil.isLanguageLevel9OrHigher(context)) {
          return "java.util.List.of";
        }
        else if (ClassUtils.findClass("com.google.common.collect.ImmutableList", context) != null) {
          return "com.google.common.collect.ImmutableList.of";
        }
      }
      return "java.util.Arrays.asList";
    }
    if (methodName.equals("emptySet") || methodName.equals("singleton")) {
      if (PsiUtil.isLanguageLevel9OrHigher(context)) {
        return "java.util.Set.of";
      }
      else if (ClassUtils.findClass("com.google.common.collect.ImmutableSet", context) != null) {
        return "com.google.common.collect.ImmutableSet.of";
      }
    }
    else if (methodName.equals("emptyMap") || methodName.equals("singletonMap")) {
      if (PsiUtil.isLanguageLevel9OrHigher(context)) {
        return "java.util.Map.of";
      }
      else if (ClassUtils.findClass("com.google.common.collect.ImmutableMap", context) != null) {
        return "com.google.common.collect.ImmutableMap.of";
      }
    }
    return null;
  }

  private static boolean isPossiblyNull(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiModifierListOwner) {
        final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
        return NullableNotNullManager.getInstance(expression.getProject()).isNullable(modifierListOwner, false);
      }
    }
    else if (ExpressionUtils.isNullLiteral(expression)) {
      return true;
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      return isPossiblyNull(conditionalExpression.getThenExpression()) || isPossiblyNull(conditionalExpression.getElseExpression());
    }
    return false;
  }
}
