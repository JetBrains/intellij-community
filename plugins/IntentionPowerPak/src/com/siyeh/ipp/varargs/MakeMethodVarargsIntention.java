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

import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public class MakeMethodVarargsIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new MakeMethodVarargsPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        makeMethodVarargs(element);
        makeMethodCallsVarargs(element);
    }

    private static void makeMethodCallsVarargs(PsiElement element) 
            throws IncorrectOperationException {
        final PsiMethod method = (PsiMethod) element.getParent();
        final Query<PsiReference> query =
                ReferencesSearch.search(method, method.getUseScope(), false);
        for (PsiReference reference : query) {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) reference.getElement();
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                continue;
            }
            final PsiExpression lastArgument = arguments[arguments.length - 1];
            if (!(lastArgument instanceof PsiArrayInitializerExpression)) {
                continue;
            }
            final PsiArrayInitializerExpression arrayInitializerExpression =
                    (PsiArrayInitializerExpression) lastArgument;
            final PsiExpression[] initializers =
                    arrayInitializerExpression.getInitializers();
            lastArgument.delete();
            for (PsiExpression initializer : initializers) {
                argumentList.add(initializer);
            }
        }
    }

    private static void makeMethodVarargs(PsiElement element)
            throws IncorrectOperationException {
        final PsiParameterList parameterList = (PsiParameterList) element;
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter lastParameter = parameters[parameters.length - 1];
        final PsiType type = lastParameter.getType();
        final PsiType componentType = type.getDeepComponentType();
        final String text = componentType.getCanonicalText();
        final PsiManager manager = element.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiParameter newParameter =
                factory.createParameterFromText(text + "... " +
                        lastParameter.getName(), element);
        lastParameter.replace(newParameter);
    }
}
