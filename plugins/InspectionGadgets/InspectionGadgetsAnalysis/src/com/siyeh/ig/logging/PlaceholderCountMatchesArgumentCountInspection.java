/*
 * Copyright 2013 Bas Leijdekkers
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
    if (argumentCount.intValue() > placeholderCount.intValue()) {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.more.problem.descriptor",
                                             argumentCount, placeholderCount);
    }
    else {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.fewer.problem.descriptor",
                                             argumentCount, placeholderCount);
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
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final int placeholderCount;
      final int argumentCount;
      if (InheritanceUtil.isInheritor(firstArgument.getType(), "org.slf4j.Marker")) {
        if (arguments.length < 2) {
          return;
        }
        final PsiExpression secondArgument = arguments[1];
        if (!ExpressionUtils.hasStringType(secondArgument)) {
          return;
        }
        final String value = (String)ExpressionUtils.computeConstantExpression(secondArgument);
        if (value == null) {
          return;
        }
        placeholderCount = countPlaceholders(value);
        argumentCount = hasThrowableType(arguments[arguments.length - 1]) ? arguments.length - 3 : arguments.length - 2;
      }
      else if (ExpressionUtils.hasStringType(firstArgument)) {
        final String value = (String)ExpressionUtils.computeConstantExpression(firstArgument);
        if (value == null) {
          return;
        }
        placeholderCount = countPlaceholders(value);
        argumentCount = hasThrowableType(arguments[arguments.length - 1]) ? arguments.length - 2 : arguments.length - 1;
      } else {
        return;
      }
      if (placeholderCount == argumentCount) {
        return;
      }
      registerMethodCallError(expression, Integer.valueOf(argumentCount), Integer.valueOf(placeholderCount));
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

    public static int countPlaceholders(String value) {
      int count = 0;
      int index = value.indexOf("{}");
      while (index >= 0) {
        if (index <= 0 || value.charAt(index - 1) != '\\') {
          count++;
        }
        index = value.indexOf("{}", index + 1);
      }
      return count;
    }
  }
}
