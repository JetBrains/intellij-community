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
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.Processor;
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
        //return new VarargParameterFix();
        return null;
    }

    private static class VarargParameterFix extends InspectionGadgetsFix {

        public String getName() {
            return "Change variable argument parameter to array parameter";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)element.getParent();
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();

            final Query<PsiReference> query = ReferencesSearch.search(method);
            query.forEach(new Processor<PsiReference>() {
                public boolean process(PsiReference reference) {
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)reference.getElement();
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression)
                                    referenceExpression.getParent();
                    final PsiExpressionList argumentList =
                            methodCallExpression.getArgumentList();
                    final PsiExpression[] arguments =
                            argumentList.getExpressions();
                    for (PsiExpression argument : arguments) {
                        System.out.println(argument);
                    }
                    return true;
                }
            });

            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (lastParameter.isVarArgs()) {
                final PsiEllipsisType type =
                        (PsiEllipsisType)lastParameter.getType();
                final PsiType arrayType = type.toArrayType();
                final PsiManager psiManager = lastParameter.getManager();
                final PsiElementFactory factory =
                        psiManager.getElementFactory();
                final PsiTypeElement newTypeElement =
                        factory.createTypeElement(arrayType);
                final PsiTypeElement typeElement =
                        lastParameter.getTypeElement();
                typeElement.replace(newTypeElement);
            }
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
            if (parameters.length < 1) {
                return;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (lastParameter.isVarArgs()) {
                registerMethodError(method);
            }
        }
    }
}