/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.varargs;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConvertVarargParameterToArrayIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertVarargParameterToArrayPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiParameterList parameterList = (PsiParameterList) element;
        convertVarargsToArray(parameterList);
    }

    private void convertVarargsToArray(PsiParameterList parameterList)
            throws IncorrectOperationException {
        final PsiParameter lastParameter =
                (PsiParameter) parameterList.getLastChild();
        if (lastParameter == null || !lastParameter.isVarArgs()) {
            return;
        }
        final PsiEllipsisType type =
                (PsiEllipsisType)lastParameter.getType();
        final PsiMethod method = (PsiMethod)parameterList.getParent();
        final Query<PsiReference> query = ReferencesSearch.search(method);
        final PsiType componentType = type.getComponentType();
        final String typeText = componentType.getCanonicalText();
        final int parameterIndex =
                parameterList.getParameterIndex(lastParameter);
        for (PsiReference reference : query) {
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) reference.getElement();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) referenceExpression.getParent();
            modifyCall(methodCallExpression, typeText, parameterIndex);
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

    public static void modifyCall(PsiMethodCallExpression methodCallExpression,
                                   String arrayTypeText,
                                   int indexOfFirstVarargArgument)
            throws IncorrectOperationException {
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        @NonNls final StringBuilder builder = new StringBuilder("new ");
        builder.append(arrayTypeText);
        builder.append("[]{");
        if (arguments.length > indexOfFirstVarargArgument) {
            final PsiExpression firstArgument =
                    arguments[indexOfFirstVarargArgument];
            final String firstArgumentText = firstArgument.getText();
            builder.append(firstArgumentText);
            for (int i = indexOfFirstVarargArgument + 1;
                 i < arguments.length; i++) {
                builder.append(',');
                builder.append(arguments[i].getText());
            }
        }
        builder.append('}');
        final PsiManager manager = methodCallExpression.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiExpression arrayExpression =
                factory.createExpressionFromText(builder.toString(),
                        methodCallExpression);
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
    }
}