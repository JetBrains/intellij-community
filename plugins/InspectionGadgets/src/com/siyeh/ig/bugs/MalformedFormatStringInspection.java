/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import java.util.HashSet;
import java.util.Set;

public class MalformedFormatStringInspection extends BaseInspection{

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "malformed.format.string.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        final Object value = infos[0];
        final Validator[] validators;
        if (value instanceof Exception) {
            return InspectionGadgetsBundle.message(
                    "malformed.format.string.problem.descriptor.malformed");
        } else {
            validators = (Validator[]) value;
        }
        final int argumentCount = ((Integer)infos[1]).intValue();
        if(validators.length < argumentCount){
            return InspectionGadgetsBundle.message(
                    "malformed.format.string.problem.descriptor.too.many.arguments");
        }
        if(validators.length > argumentCount){
            return InspectionGadgetsBundle.message(
                    "malformed.format.string.problem.descriptor.too.few.arguments");
        }
        return InspectionGadgetsBundle.message(
                "malformed.format.string.problem.descriptor.arguments.do.not.match.type");
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new MalformedFormatStringVisitor();
    }

    private static class MalformedFormatStringVisitor
            extends BaseInspectionVisitor{

        /** @noinspection StaticCollection */
        @NonNls
        private static final Set<String> formatMethodNames =
                new HashSet<String>(2);

        /** @noinspection StaticCollection */
        private static final Set<String> formatClassNames =
                new HashSet<String>(4);

        static{
            formatMethodNames.add("format");
            formatMethodNames.add("printf");

            formatClassNames.add("java.io.PrintWriter");
            formatClassNames.add("java.io.PrintStream");
            formatClassNames.add("java.util.Formatter");
            formatClassNames.add("java.lang.String");
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if(arguments.length == 0){
                return;
            }
            final PsiExpression firstArgument = arguments[0];
            final PsiType type = firstArgument.getType();
            if(type == null){
                return;
            }
            final int formatArgPosition;
            if("java.util.Locale".equals(type.getCanonicalText())
                    && arguments.length > 1){
                formatArgPosition = 1;
            } else{
                formatArgPosition = 0;
            }
            final PsiExpression formatArgument = arguments[formatArgPosition];
            if(!TypeUtils.expressionHasType("java.lang.String",
                    formatArgument)){
                return;
            }
            if(!PsiUtil.isConstantExpression(formatArgument)){
                return;
            }
            final PsiType formatType = formatArgument.getType();
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(
                            formatArgument, formatType);
            if(value == null){
                return;
            }
            if(!callTakesFormatString(expression)){
                return;
            }
            final int argumentCount = arguments.length - (formatArgPosition + 1);
            final Validator[] validators;
            try{
                validators = FormatDecode.decode(value, argumentCount);
            } catch(Exception e){
                registerError(formatArgument, e);
                return;
            }
            if(validators.length != argumentCount){
                registerError(formatArgument, validators,
                        Integer.valueOf(argumentCount));
                return;
            }
            for(int i = 0; i < validators.length; i++){
                final Validator validator = validators[i];
                final PsiType argType =
                        arguments[i + formatArgPosition + 1].getType();
                if(!validator.valid(argType)){
                    registerError(formatArgument, validators,
                            Integer.valueOf(argumentCount));
                    return;
                }
            }
        }

        private static boolean callTakesFormatString(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
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
            return formatClassNames.contains(className);
        }
    }
}