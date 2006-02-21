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
import com.intellij.psi.codeStyle.CodeStyleManager;
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

import java.util.Collection;

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
            return InspectionGadgetsBundle.message(
                    "variable.argument.method.quick.fix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)element.getParent();
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            final PsiEllipsisType type =
                    (PsiEllipsisType)lastParameter.getType();
            final Query<PsiReference> query = ReferencesSearch.search(method);
            final PsiType componentType = type.getComponentType();
            final String typeText = componentType.getCanonicalText();
            //query.forEach(new VarargProcessor(typeText, parameters.length - 1));
            final VarargProcessor processor =
                    new VarargProcessor(
                            typeText, parameters.length - 1);
            final Collection<PsiReference> references = query.findAll();
            for (PsiReference reference : references) {
                processor.process(reference);
            }
            final PsiType arrayType = type.toArrayType();
            final PsiManager psiManager = lastParameter.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(arrayType);
            final PsiTypeElement typeElement =
                    lastParameter.getTypeElement();
            typeElement.replace(newTypeElement);
        }

        private static class VarargProcessor
                implements Processor<PsiReference> {

            private final String arrayTypeText;
            private final int indexOfFirstVarargArgument;

            public VarargProcessor(String arrayTypeText,
                                   int indexOfFirstVarargArgument) {
                this.arrayTypeText = arrayTypeText;
                this.indexOfFirstVarargArgument = indexOfFirstVarargArgument;
            }

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
                StringBuilder builder = new StringBuilder("new ");
                builder.append(arrayTypeText);
                builder.append("[]{");
                if (arguments.length > indexOfFirstVarargArgument) {
                    final PsiExpression firstArgument =
                            arguments[indexOfFirstVarargArgument];
                    final String firstArgumentText =
                            firstArgument.getText();
                    builder.append(firstArgumentText);
                    for (int i = indexOfFirstVarargArgument + 1;
                         i < arguments.length; i++) {
                        final PsiExpression argument = arguments[i];
                        builder.append(',');
                        builder.append(argument.getText());
                    }
                }
                builder.append('}');
                final PsiManager manager = referenceExpression.getManager();
                final PsiElementFactory factory = manager.getElementFactory();
                try {
                    final PsiExpression arrayExpression =
                            factory.createExpressionFromText(builder.toString(),
                                    referenceExpression);
                    if (arguments.length > indexOfFirstVarargArgument) {
                        final PsiExpression firstArgument =
                                arguments[indexOfFirstVarargArgument];
                        argumentList.deleteChildRange(firstArgument,
                                arguments[arguments.length - 1]);
                        argumentList.add(arrayExpression);
                    } else {
                        argumentList.add(arrayExpression);
                    }
                    final CodeStyleManager codeStyleManager =
                            manager.getCodeStyleManager();
                    codeStyleManager.shortenClassReferences(argumentList);
                    codeStyleManager.reformat(argumentList);
                } catch (IncorrectOperationException e) {
                    throw new RuntimeException(e);
                }
                return true;
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