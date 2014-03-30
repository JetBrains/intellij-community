/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MalformedRegexInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("malformed.regular.expression.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("malformed.regular.expression.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message("malformed.regular.expression.problem.descriptor2", infos[0]);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedRegexVisitor();
  }

  private static class MalformedRegexVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      if (!MethodCallUtils.isCallToRegexMethod(methodCallExpression)) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      if (!ExpressionUtils.hasStringType(firstArgument)) {
        return;
      }
      final String value = (String)ExpressionUtils.computeConstantExpression(firstArgument);
      if (value == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if ("compile".equals(methodExpression.getReferenceName()) && arguments.length == 2) {
        final PsiExpression secondArgument = arguments[1];
        final Object flags = ExpressionUtils.computeConstantExpression(secondArgument);
        if (flags instanceof Integer) {
          try {
            Pattern.compile(value, ((Integer)flags).intValue());
          }
          catch (PatternSyntaxException e) {
            registerError(firstArgument, e.getDescription());
          }
          catch (NullPointerException e) {
            registerError(firstArgument);
          }
        }
        return;
      }
      //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
      try {
        Pattern.compile(value);
      }
      catch (PatternSyntaxException e) {
        registerError(firstArgument, e.getDescription());
      }
      catch (NullPointerException e) {
        registerError(firstArgument); // due to a bug in the sun regex code
      }
    }
  }
}