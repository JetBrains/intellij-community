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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class NullArgumentToVariableArgMethodInspection extends ExpressionInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("null.argument.to.var.arg.method.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("null.argument.to.var.arg.method.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NullArgumentToVariableArgVisitor();
    }

    private static class NullArgumentToVariableArgVisitor
            extends BaseInspectionVisitor{


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiManager manager = call.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
                       languageLevel.equals(LanguageLevel.JDK_1_4)){
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args.length == 0){
                return;
            }
            final PsiExpression lastArg = args[args.length - 1];
            if(!isNull(lastArg)){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null){
                return;
            }
            if(parameters.length != args.length){
                return;
            }
            final PsiParameter lastParameter = parameters[parameters.length - 1];
            if(!lastParameter.isVarArgs())
            {
                return;
            }
            registerError(lastArg);
        }

        private static boolean isNull(PsiExpression arg){
            if(!(arg instanceof PsiLiteralExpression)){
                return false;
            }
            final String text = arg.getText();
            return PsiKeyword.NULL.equals(text);
        }
    }
}
