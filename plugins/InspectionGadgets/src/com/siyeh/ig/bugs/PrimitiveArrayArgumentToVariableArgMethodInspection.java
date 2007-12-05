/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class PrimitiveArrayArgumentToVariableArgMethodInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "primitive.array.argument.to.var.arg.method.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "primitive.array.argument.to.var.arg.method.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PrimitiveArrayArgumentToVariableArgVisitor();
    }

    private static class PrimitiveArrayArgumentToVariableArgVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            final PsiExpression lastArgument = arguments[arguments.length - 1];
            final PsiType argumentType = lastArgument.getType();
            if (!isPrimitiveArrayType(argumentType)) {
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != arguments.length) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            final PsiType parameterType = lastParameter.getType();
            if (isPrimitiveArrayType(parameterType)) {
                return;
            }
            registerError(lastArgument);
        }
    }

    private static boolean isPrimitiveArrayType(PsiType type) {
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiArrayType)) {
            return false;
        }
        final PsiType componentType = ((PsiArrayType)type).getComponentType();
        return TypeConversionUtil.isPrimitiveAndNotNull(componentType);
    }
}