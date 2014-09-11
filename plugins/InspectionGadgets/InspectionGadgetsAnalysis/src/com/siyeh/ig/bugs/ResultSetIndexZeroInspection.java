/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ResultSetIndexZeroInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "UseOfIndexZeroInJDBCResultSet";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("use.0index.in.jdbc.resultset.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return InspectionGadgetsBundle.message("use.0index.in.jdbc.resultset.problem.descriptor");
    } else {
      return InspectionGadgetsBundle.message("use.0index.in.jdbc.prepared.statement.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ResultSetIndexZeroVisitor();
  }

  private static class ResultSetIndexZeroVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (methodName == null) {
        return;
      }
      final boolean resultSet;
      if (methodName.startsWith("get") || methodName.startsWith("update")) {
        resultSet = true;
      } else if (methodName.startsWith("set")) {
        resultSet = false;
      } else {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final Object val = ExpressionUtils.computeConstantExpression(argument);
      if (!(val instanceof Integer) || ((Integer)val).intValue() != 0) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (resultSet) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.sql.ResultSet")) {
          registerError(argument, Boolean.TRUE);
        }
      } else if (arguments.length > 1) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.sql.PreparedStatement")) {
          registerError(argument, Boolean.FALSE);
        }
      }
    }
  }
}