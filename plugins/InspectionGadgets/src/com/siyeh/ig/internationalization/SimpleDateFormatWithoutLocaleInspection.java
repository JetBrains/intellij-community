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
package com.siyeh.ig.internationalization;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiNewExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SimpleDateFormatWithoutLocaleInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "instantiating.simpledateformat.without.locale.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "instantiating.simpledateformat.without.locale.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SimpleDateFormatWithoutLocaleVisitor();
    }

    private static class SimpleDateFormatWithoutLocaleVisitor
            extends BaseInspectionVisitor {

        @Override public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if(!TypeUtils.expressionHasType("java.text.SimpleDateFormat",
                    expression)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            for(PsiExpression arg : args){
                if(TypeUtils.expressionHasType("java.util.Locale", arg)){
                    return;
                }
            }
            registerError(expression);
        }
    }
}