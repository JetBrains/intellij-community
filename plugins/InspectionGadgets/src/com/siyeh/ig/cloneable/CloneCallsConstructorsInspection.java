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
package com.siyeh.ig.cloneable;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class CloneCallsConstructorsInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "clone.instantiates.objects.with.constructor.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "clone.instantiates.objects.with.constructor.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CloneCallsConstructorVisitor();
    }

    private static class CloneCallsConstructorVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final String methodName = method.getName();
            final PsiParameterList parameterList = method.getParameterList();
            final boolean isClone =
                    HardcodedMethodConstants.CLONE.equals(methodName) && parameterList.getParametersCount() == 0;
            if (isClone) {
                method.accept(new PsiRecursiveElementVisitor() {

                    public void visitNewExpression(
                            @NotNull PsiNewExpression newExpression) {
                        super.visitNewExpression(newExpression);
                        final PsiExpression[] arrayDimensions =
                                newExpression.getArrayDimensions();
                        if (arrayDimensions.length != 0) {
                            return;
                        }
                        if (newExpression.getArrayInitializer() != null) {
                            return;
                        }
                        if (newExpression.getAnonymousClass() != null) {
                            return;
                        }
                        if (PsiTreeUtil.getParentOfType(newExpression,
                                PsiThrowStatement.class) != null) {
                            return;
                        }
                        final PsiJavaCodeReferenceElement classReference =
                                newExpression.getClassReference();
                        registerError(classReference);
                    }
                });
            }
        }
    }
}