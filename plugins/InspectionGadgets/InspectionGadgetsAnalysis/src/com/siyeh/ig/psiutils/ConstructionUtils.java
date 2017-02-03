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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;

/**
 * @author Tagir Valeev
 */
public class ConstructionUtils {
  /**
   * Checks that given expression initializes empty StringBuilder or StringBuffer (either with explicit default capacity or not)
   *
   * @param initializer initializer to check
   * @return true if the initializer is empty StringBuilder or StringBuffer initializer
   */
  @Contract("null -> false")
  public static boolean isEmptyStringBuilderInitializer(PsiExpression initializer) {
    initializer = PsiUtil.skipParenthesizedExprDown(initializer);
    if (!(initializer instanceof PsiNewExpression)) return false;
    final PsiNewExpression newExpression = (PsiNewExpression)initializer;
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return false;
    final PsiElement target = classReference.resolve();
    if (!(target instanceof PsiClass)) return false;
    final PsiClass aClass = (PsiClass)target;
    final String qualifiedName = aClass.getQualifiedName();
    if (!CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(qualifiedName) &&
        !CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(qualifiedName)) {
      return false;
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return false;
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) return true;
    final PsiExpression argument = arguments[0];
    final PsiType argumentType = argument.getType();
    return PsiType.INT.equals(argumentType);
  }

  /**
   * Checks that given expression initializes empty Collection or Map
   *
   * @param expression expression to check
   * @return true if the expression is the empty Collection or Map initializer
   */
  @Contract("null -> false")
  public static boolean isEmptyCollectionInitializer(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiNewExpression) {
      PsiExpressionList argumentList = ((PsiNewExpression)expression).getArgumentList();
      if (argumentList == null || argumentList.getExpressions().length != 0) return false;
      PsiType type = expression.getType();
      return com.intellij.psi.util.InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
             com.intellij.psi.util.InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      String name = call.getMethodExpression().getReferenceName();
      PsiExpressionList argumentList = call.getArgumentList();
      if(name != null && name.startsWith("new") && argumentList.getExpressions().length == 0) {
        PsiMethod method = call.resolveMethod();
        if(method != null && method.getParameterList().getParametersCount() == 0) {
          PsiClass aClass = method.getContainingClass();
          if(aClass != null) {
            String qualifiedName = aClass.getQualifiedName();
            if("com.google.common.collect.Maps".equals(qualifiedName) ||
               "com.google.common.collect.Lists".equals(qualifiedName) ||
               "com.google.common.collect.Sets".equals(qualifiedName)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
