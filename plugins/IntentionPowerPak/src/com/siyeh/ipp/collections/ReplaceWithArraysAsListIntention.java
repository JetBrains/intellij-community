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

import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWithArraysAsListIntention extends Intention {

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
      final String name = method.getName();
      if (!name.equals("emptyList") && !name.equals("singletonList")) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String qualifiedName = aClass.getQualifiedName();
      return qualifiedName != null && qualifiedName.equals("java.util.Collections");
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, "java.util.Arrays.asList" + argumentList.getText());
  }
}
