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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ThrownExceptionsPerMethodInspection
        extends MethodMetricInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "thrown.exceptions.per.method.display.name");
    }

    public String getID() {
        return "MethodWithTooExceptionsDeclared";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod)location.getParent();
        assert method != null;
        final PsiReferenceList throwsList = method.getThrowsList();
        final int numThrows = throwsList.getReferenceElements().length;
        return InspectionGadgetsBundle.message(
                "thrown.exceptions.per.method.problem.descriptor",
                Integer.valueOf(numThrows));
    }

    protected int getDefaultLimit() {
        return 3;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message(
                "thrown.exceptions.per.method.limit.option");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrownExceptionsPerMethodVisitor();
    }

    private class ThrownExceptionsPerMethodVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final PsiReferenceList throwList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] thrownExceptions =
                    throwList.getReferenceElements();
            if (thrownExceptions.length <= getLimit()) {
                return;
            }
            registerMethodError(method);
        }
    }
}