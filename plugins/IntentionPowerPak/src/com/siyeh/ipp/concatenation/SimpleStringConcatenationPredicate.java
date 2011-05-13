/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class SimpleStringConcatenationPredicate
        implements PsiElementPredicate{

    private final boolean excludeConcatentationsInsideAnnotations;

    public SimpleStringConcatenationPredicate(boolean excludeConcatentationsInsideAnnotations) {
        this.excludeConcatentationsInsideAnnotations = excludeConcatentationsInsideAnnotations;
    }

    public boolean satisfiedBy(PsiElement element){
        if(!ConcatenationUtils.isConcatenation(element)){
            return false;
        }
        if (excludeConcatentationsInsideAnnotations && isInsideAnnotation(element)) {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }

    private static boolean isInsideAnnotation(PsiElement element) {
        for (int i = 0; i < 20 && element instanceof PsiBinaryExpression; i++) {
            // optimization: don't check deep string concatenation more than 20 levels up.
            element = element.getParent();
            if (element instanceof PsiNameValuePair ||
                    element instanceof PsiArrayInitializerMemberValue) {
                return true;
            }
        }
        return false;
    }
}
