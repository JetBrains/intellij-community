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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VarargParameterInspection extends MethodInspection {

    public String getID(){
        return "VariableArgumentMethod";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "variable.argument.method.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }


    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new VarargParameterFix();
    }

    private static class VarargParameterFix extends InspectionGadgetsFix {

        public String getName() {
            return "Change variable argument parameter to array parameter";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
        }
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "variable.argument.method.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new VarargParameterVisitor();
    }

    private static class VarargParameterVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            for(final PsiParameter parameter : parameters){
                if(parameter.isVarArgs()){
                    registerMethodError(method);
                    return;
                }
            }
        }
    }
}