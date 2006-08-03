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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class MakeMethodVarargsPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(@NotNull PsiElement element) {
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(element);
        if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
            return false;
        }
        if (!(element instanceof PsiParameterList)) {
            return false;
        }
        final PsiParameterList parameterList = (PsiParameterList) element;
        if (!(element.getParent() instanceof PsiMethod)) {
            return false;
        }
        if (parameterList.getParametersCount() == 0) {
            return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter lastExpression =
                parameters[parameters.length - 1];
        final PsiType type = lastExpression.getType();
        return type instanceof PsiArrayType;
    }
}