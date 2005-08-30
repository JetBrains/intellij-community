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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ObjectEqualsNullInspection extends ExpressionInspection {

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String getDisplayName() {
        return "Object.equals(null)";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("object.equals.null.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualsNullVisitor();
    }

    private static class ObjectEqualsNullVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            if(!IsEqualsUtil.isEquals(call)){
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            if (!isNull(args[0])) {
                return;
            }
            registerError(args[0]);
        }

        private static boolean isNull(PsiExpression arg) {
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            final String text = arg.getText();
            return PsiKeyword.NULL.equals(text);
        }

    }

}
