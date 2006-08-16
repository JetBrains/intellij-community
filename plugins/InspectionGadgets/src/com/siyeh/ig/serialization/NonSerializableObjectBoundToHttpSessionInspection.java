/*
 * Copyright 2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableObjectBoundToHttpSessionInspection
        extends ClassInspection {

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.serializable.object.bound.to.http.session.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableObjectBoundToHttpSessionVisitor();
    }

    private static class NonSerializableObjectBoundToHttpSessionVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression methodCallExpression) {
            super.visitMethodCallExpression(methodCallExpression);
            if (!MethodCallUtils.isSimpleCallToMethod(methodCallExpression,
                    "javax.servlet.http.HttpSession", PsiType.VOID,
                    "putValue", "java.lang.String", "java.lang.Object") &&
                    !MethodCallUtils.isSimpleCallToMethod(methodCallExpression,
                            "javax.servlet.http.HttpSession", PsiType.VOID,
                            "setAttribute", "java.lang.String",
                            "java.lang.Object")) {
                return;
            }
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if(arguments.length != 2) {
                return;
            }
            final PsiExpression argument = arguments[1];
            final PsiType argumentType = argument.getType();
            if(argumentType == null) {
                return;
            }
            if (SerializationUtils.isProbablySerializable(argumentType)) {
                return;
            }
            registerError(argument);
        }
    }
}