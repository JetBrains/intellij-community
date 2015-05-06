/*
 * Copyright 2013-2015 Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PlaceholderCountMatchesArgumentCountInspection extends BaseInspection {

  @NonNls
  private static final Set<String> loggingMethodNames = ContainerUtilRt.newHashSet("trace", "debug", "info", "warn", "error");

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Integer argumentCount = (Integer)infos[0];
    final Integer placeholderCount = (Integer)infos[1];
    final Object value = infos[2];
    if (argumentCount.intValue() > placeholderCount.intValue()) {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.more.problem.descriptor",
                                             argumentCount, placeholderCount, value);
    }
    else {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.fewer.problem.descriptor",
                                             argumentCount, placeholderCount, value);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PlaceholderCountMatchesArgumentCountVisitor();
  }

  private static class PlaceholderCountMatchesArgumentCountVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!loggingMethodNames.contains(name)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(aClass, "org.slf4j.Logger")) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression logStringArgument = arguments[0];
      final int argumentCount;
      if (InheritanceUtil.isInheritor(logStringArgument.getType(), "org.slf4j.Marker")) {
        if (arguments.length < 2) {
          return;
        }
        logStringArgument = arguments[1];
        argumentCount = countArguments(arguments, 2);
      }
      else {
        argumentCount = countArguments(arguments, 1);
      }
      final Object value = ExpressionUtils.computeConstantExpression(logStringArgument);
      final int placeholderCount = countPlaceholders(value);
      if (placeholderCount < 0 || argumentCount < 0 || placeholderCount == argumentCount) {
        return;
      }
      if (placeholderCount > 1 && placeholderCount == argumentCount + 1 && hasThrowableType(arguments[arguments.length - 1])) {
        // if there is more than one argument and the last argument is an exception, but there is a placeholder for
        // the exception, then the stack trace won't be logged.
        return;
      }
      registerError(logStringArgument, Integer.valueOf(argumentCount), Integer.valueOf(placeholderCount), value);
    }

    private static boolean hasThrowableType(PsiExpression lastArgument) {
      final PsiType type = lastArgument.getType();
      if (type instanceof PsiDisjunctionType) {
        final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        for (PsiType disjunction : disjunctionType.getDisjunctions()) {
          if (!InheritanceUtil.isInheritor(disjunction, CommonClassNames.JAVA_LANG_THROWABLE)) {
            return false;
          }
        }
        return true;
      }
      return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE);
    }

    public static int countPlaceholders(Object value) {
      if (!(value instanceof String)) {
        return -1;
      }
      final String string = (String)value;
      int count = 0;
      int index = string.indexOf("{}");
      while (index >= 0) {
        if (index == 0 || string.charAt(index - 1) != '\\') {
          count++;
        }
        index = string.indexOf("{}", index + 1);
      }
      return count;
    }

    private static int countArguments(PsiExpression[] arguments, int countFrom) {
      if (arguments.length <= countFrom) {
        return 0;
      }
      final int count = arguments.length - countFrom;
      if (count == 1) {
        final PsiExpression argument = arguments[countFrom];
        final PsiType argumentType = argument.getType();
        if (argumentType instanceof PsiArrayType) {
          if (argumentType.equalsToText("java.lang.Object[]") && argument instanceof PsiNewExpression) {
            final PsiNewExpression newExpression = (PsiNewExpression)argument;
            final PsiArrayInitializerExpression arrayInitializerExpression = newExpression.getArrayInitializer();
            if (arrayInitializerExpression != null) {
              return arrayInitializerExpression.getInitializers().length;
            }
          }
          return -1;
        }
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      return hasThrowableType(lastArgument) ? count - 1 : count;
    }
  }
}
