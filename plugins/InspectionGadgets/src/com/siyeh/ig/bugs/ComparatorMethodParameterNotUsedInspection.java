/*
 * Copyright 2007 Bas Leijdekkers
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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ComparatorMethodParameterNotUsedInspection
        extends BaseInspection {

    @Nls @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "comparator.method.parameter.not.used.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "comparator.method.parameter.not.used.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CompareMethodDoesNotUseParameterVisitor();
    }

    private static class CompareMethodDoesNotUseParameterVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!MethodUtils.methodMatches(method, "java.util.Comparator",
                    PsiType.INT, "compare", PsiType.NULL, PsiType.NULL)) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final ParameterAccessVisitor visitor =
                    new ParameterAccessVisitor(parameters);
            body.accept(visitor);
            final Collection<PsiParameter> unusedParameters =
                    visitor.getUnusedParameters();
            for (PsiParameter unusedParameter : unusedParameters) {
                registerVariableError(unusedParameter);
            }
        }

        private static class ParameterAccessVisitor
                extends PsiRecursiveElementVisitor {

            private final Set<PsiParameter> parameters;

            ParameterAccessVisitor(@NotNull PsiParameter[] parameters) {
                this.parameters = new HashSet(Arrays.asList(parameters));
            }

            public void visitReferenceExpression(
                    PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (parameters.isEmpty()) {
                    return;
                }
                if (expression.getQualifierExpression() != null) {
                    // optimization
                    // references to parameters are never qualified
                    return;
                }
                final PsiElement target = expression.resolve();
                if (!(target instanceof PsiParameter)) {
                    return;
                }
                final PsiParameter parameter = (PsiParameter) target;
                parameters.remove(parameter);
            }

            public Collection<PsiParameter> getUnusedParameters() {
                return Collections.unmodifiableSet(parameters);
            }
        }
    }
}