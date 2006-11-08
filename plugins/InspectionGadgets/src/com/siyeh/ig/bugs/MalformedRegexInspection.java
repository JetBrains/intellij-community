/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MalformedRegexInspection extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "malformed.regular.expression.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        if (infos.length == 0) {
            return InspectionGadgetsBundle.message(
                    "malformed.regular.expression.problem.descriptor1");
        } else {
            return InspectionGadgetsBundle.message(
                    "malformed.regular.expression.problem.descriptor2",
                    infos[0]);
        }
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MalformedRegexVisitor();
    }

    private static class MalformedRegexVisitor extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argList = expression.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args.length == 0){
                return;
            }
            final PsiExpression regexArg = args[0];
            if(!TypeUtils.expressionHasType("java.lang.String", regexArg)){
                return;
            }
            if(!PsiUtil.isConstantExpression(regexArg)){
                return;
            }
            final PsiType regexType = regexArg.getType();
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(regexArg,
                            regexType);
            if(value == null){
                return;
            }
            if(!MethodCallUtils.isCallToRegexMethod(expression)){
                return;
            }
            //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
            try{
                Pattern.compile(value);
            } catch(PatternSyntaxException e){
                registerError(regexArg, e.getDescription());
            } catch(NullPointerException e){
                registerError(regexArg); // due to a bug in the sun regex code
            }
        }
    }
}