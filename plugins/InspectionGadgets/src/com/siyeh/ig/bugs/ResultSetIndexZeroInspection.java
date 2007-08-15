/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ResultSetIndexZeroInspection extends BaseInspection {

    @NotNull
    public String getID(){
        return "UseOfIndexZeroInJDBCResultSet";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "use.0index.in.jdbc.resultset.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "use.0index.in.jdbc.resultset.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ResultSetIndexZeroVisitor();
    }

    private static class ResultSetIndexZeroVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if(methodName == null){
                return;
            }
            if (!methodName.startsWith("get") &&
                    !methodName.startsWith("update") ) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if(arguments.length== 0){
                return;
            }
            final PsiExpression argument = arguments[0];
            if (!TypeUtils.expressionHasType(PsiKeyword.INT, argument)) {
                return;
            }
            if(!PsiUtil.isConstantExpression(argument)) {
                return;
            }
            final Integer val =
                    (Integer) ConstantExpressionUtil.computeCastTo(argument,
                            PsiType.INT);
            if(val == null || val.intValue() != 0) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, 
		            "java.sql.ResultSet")) {
                return;
            }
            registerError(argument);
        }
    }
}