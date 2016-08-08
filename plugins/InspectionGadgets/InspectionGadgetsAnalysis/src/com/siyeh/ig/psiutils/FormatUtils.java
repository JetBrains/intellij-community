/*
 * Copyright 2010-2016 Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FormatUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  public static final Set<String> formatMethodNames = new HashSet<>(2);
  /**
   * @noinspection StaticCollection
   */
  public static final Set<String> formatClassNames = new HashSet<>(4);

  static {
    formatMethodNames.add("format");
    formatMethodNames.add("printf");

    formatClassNames.add("java.io.Console");
    formatClassNames.add("java.io.PrintWriter");
    formatClassNames.add("java.io.PrintStream");
    formatClassNames.add("java.util.Formatter");
    formatClassNames.add(CommonClassNames.JAVA_LANG_STRING);
  }

  private FormatUtils() {}

  public static boolean isFormatCall(PsiMethodCallExpression expression) {
    return isFormatCall(expression, Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  public static boolean isFormatCall(PsiMethodCallExpression expression, List<String> optionalMethods, List<String> optionalClasses) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!formatMethodNames.contains(name) && !optionalMethods.contains(name)) {
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
    return formatClassNames.contains(className) || optionalClasses.contains(className);
  }

  public static boolean isFormatCallArgument(PsiElement element) {
    final PsiExpressionList expressionList =
      PsiTreeUtil.getParentOfType(element, PsiExpressionList.class, true, PsiCodeBlock.class, PsiStatement.class, PsiClass.class);
    if (expressionList == null) {
      return false;
    }
    final PsiElement parent = expressionList.getParent();
    return parent instanceof PsiMethodCallExpression && isFormatCall((PsiMethodCallExpression)parent);
  }

  @Nullable
  public static PsiExpression getFormatArgument(PsiExpressionList argumentList) {
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) {
      return null;
    }
    final PsiExpression firstArgument = arguments[0];
    final PsiType type = firstArgument.getType();
    if (type == null) {
      return null;
    }
    final int formatArgumentIndex;
    if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
      formatArgumentIndex = 1;
    }
    else {
      formatArgumentIndex = 0;
    }
    return arguments[formatArgumentIndex];
  }
}
