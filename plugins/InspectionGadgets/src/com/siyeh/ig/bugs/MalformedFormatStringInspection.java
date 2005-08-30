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

import java.util.HashSet;
import java.util.Set;

public class MalformedFormatStringInspection extends ExpressionInspection{
    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> formatMethodNames = new HashSet<String>(5);

    static {
      //noinspection HardCodedStringLiteral
      formatMethodNames.add("format");
      //noinspection HardCodedStringLiteral
      formatMethodNames.add("printf");
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("malformed.format.string.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiExpression formatArg = (PsiExpression) location;
        final PsiExpressionList argList = (PsiExpressionList) formatArg
                .getParent();
        assert argList != null;
        final PsiExpression[] args = argList.getExpressions();
        int formatArgPosition = 0;
        for(int i = 0; i < args.length; i++){
            if(formatArg.equals(args[i]))
            {
                formatArgPosition = i;
                break;
            }
        }
        final PsiType formatType = formatArg.getType();
        final String value =
                (String) ConstantExpressionUtil
                        .computeCastTo(formatArg, formatType);

        final Validator[] validators;
        try{
            validators = FormatDecode.decode(value);
        }  catch(Exception ignore){
            return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.malformed");
        }
        final int numArgs = args.length - (formatArgPosition + 1);
        if(validators.length < numArgs){
            return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.many.arguments");
        }
        if(validators.length > numArgs){
            return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.too.few.arguments");
        }
        return InspectionGadgetsBundle.message("malformed.format.string.problem.descriptor.arguments.do.not.match.type");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MalformedFormatStringVisitor();
    }

    private static class MalformedFormatStringVisitor
            extends BaseInspectionVisitor{
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

            final PsiExpression firstArg = args[0];
            final PsiType type = firstArg.getType();
            if(type == null){
                return;
            }
            final int formatArgPosition;
            if("java.util.Locale".equals(type.getCanonicalText())
                    && args.length > 1){
                formatArgPosition = 1;
            } else{
                formatArgPosition = 0;
            }
            final PsiExpression formatArg = args[formatArgPosition];

            if(!TypeUtils.expressionHasType("java.lang.String", formatArg)){
                return;
            }
            if(!PsiUtil.isConstantExpression(formatArg)){
                return;
            }
            final PsiType formatType = formatArg.getType();
            final String value =
                    (String) ConstantExpressionUtil
                            .computeCastTo(formatArg, formatType);
            if(value == null){
                return;
            }
            if(!callTakesFormatString(expression)){
                return;
            }
            final Validator[] validators;
            try{
                validators = FormatDecode.decode(value);
            } catch(Exception ignore){
                registerError(formatArg);
                return;
            }

            if(validators.length != args.length -(formatArgPosition+ 1)){
                registerError(formatArg);
                return;
            }
            for(int i = 0; i < validators.length; i++){
                final Validator validator = validators[i];
                final PsiType argType = args[i + formatArgPosition + 1].getType();
                if(!validator.valid(argType)){
                    registerError(formatArg);
                    return;
                }
            }
        }

        private static boolean callTakesFormatString(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String name = methodExpression.getReferenceName();
            if(!formatMethodNames.contains(name)){
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
            return "java.io.PrintWriter".equals(className) ||
                    "java.io.PrintStream".equals(className);
        }
    }
}
