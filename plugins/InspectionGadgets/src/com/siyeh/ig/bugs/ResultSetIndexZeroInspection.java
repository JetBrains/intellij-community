/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ResultSetIndexZeroInspection extends ExpressionInspection {
    public String getID(){
        return "UseOfIndexZeroInJDBCResultSet";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("use.0index.in.jdbc.resultset.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("use.0index.in.jdbc.resultset.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ResultSetIndexZeroVisitor();
    }

    private static class ResultSetIndexZeroVisitor extends BaseInspectionVisitor {

        @SuppressWarnings({"HardCodedStringLiteral"})
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null)
            {
                return;
            }
            if (!methodName.startsWith("get") && !methodName.startsWith("update") ) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args == null) {
                return;
            }
            if(args.length== 0)
            {
                return;
            }
            final PsiExpression arg = args[0];
            if (!TypeUtils.expressionHasType(PsiKeyword.INT, arg)) {
                return;
            }
            if(!PsiUtil.isConstantExpression(arg))
            {
                return;
            }
            final Integer val = (Integer) ConstantExpressionUtil.computeCastTo(arg, PsiType.INT);
            if(val == null || val!=0)
            {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!TypeUtils.expressionHasTypeOrSubtype("java.sql.ResultSet", qualifier)) {
                return;
            }
            registerError(arg);
        }
    }

}
