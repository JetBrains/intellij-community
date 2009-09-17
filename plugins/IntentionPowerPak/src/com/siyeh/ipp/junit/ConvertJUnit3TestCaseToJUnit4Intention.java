/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ConvertJUnit3TestCaseToJUnit4Intention extends Intention {

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertJUnit3TestCaseToJUnit4Predicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiClass)) {
            return;
        }
        PsiClass aClass = (PsiClass) parent;
        final PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList == null) {
            return;
        }
        final Project project = element.getProject();
        final JavaCodeStyleManager codeStyleManager =
                JavaCodeStyleManager.getInstance(project);
        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            final String name = method.getName();
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            final PsiType returnType = method.getReturnType();
            if (!PsiType.VOID.equals(returnType)) {
                continue;
            }
            final PsiModifierList modifierList = method.getModifierList();
            if (name.startsWith("test")) {
                final PsiAnnotation annotation =
                        modifierList.addAnnotation("org.junit.Test");
                codeStyleManager.shortenClassReferences(annotation);
                method.accept(new MethodCallModifier());
            } else if (name.equals("setUp")) {
                final PsiAnnotation annotation =
                        modifierList.addAnnotation("org.junit.Before");
                codeStyleManager.shortenClassReferences(annotation);
            } else if (name.equals("tearDown")) {
                final PsiAnnotation annotation =
                        modifierList.addAnnotation("org.junit.After");
                codeStyleManager.shortenClassReferences(annotation);
            }
        }
        final PsiJavaCodeReferenceElement[] referenceElements =
                extendsList.getReferenceElements();
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
            referenceElement.delete();
        }
    }

    private class MethodCallModifier extends JavaRecursiveElementVisitor {

        @Override
        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if (methodExpression.getQualifierExpression() != null) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String name = aClass.getQualifiedName();
            if (!"junit.framework.Assert".equals(name)) {
                return;
            }
            final String newExpressionText =
                    "org.junit.Assert." + expression.getText();
            final Project project = expression.getProject();
            final PsiElementFactory factory =
                    JavaPsiFacade.getElementFactory(project);
            final PsiExpression newExpression =
                    factory.createExpressionFromText(newExpressionText,
                            expression);
            final JavaCodeStyleManager codeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            final PsiElement replacedExpression =
                    expression.replace(newExpression);
            codeStyleManager.shortenClassReferences(replacedExpression);
        }
    }
}
