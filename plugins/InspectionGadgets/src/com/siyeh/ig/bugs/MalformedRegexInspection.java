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
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MalformedRegexInspection extends ExpressionInspection{

    /** @noinspection StaticCollection*/
    @NonNls private static final Set<String> regexMethodNames =
            new HashSet<String>(5);

    static{
        regexMethodNames.add("compile");
        regexMethodNames.add("matches");
        regexMethodNames.add("replaceFirst");
        regexMethodNames.add("replaceAll");
        regexMethodNames.add("split");
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "malformed.regular.expression.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "malformed.regular.expression.problem.descriptor");
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
            if(!callTakesRegex(expression)){
                return;
            }
            //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
            try{
                Pattern.compile(value);
            } catch(PatternSyntaxException e){
                registerError(regexArg);
            } catch(NullPointerException e){
                registerError(regexArg); // due to a bug in the sun regex code
            }
        }

        private static boolean callTakesRegex(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if(!regexMethodNames.contains(name)){
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return false;
            }
            final String className = containingClass.getQualifiedName();
            return "java.lang.String".equals(className) ||
                           "java.util.regex.Pattern".equals(className);
        }
    }
}