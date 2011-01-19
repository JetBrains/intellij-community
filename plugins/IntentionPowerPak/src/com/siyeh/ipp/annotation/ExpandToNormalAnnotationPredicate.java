/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.siyeh.ipp.annotation;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class ExpandToNormalAnnotationPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiAnnotation)) {
            return false;
        }
        final PsiAnnotation annotation = (PsiAnnotation) element;
        final PsiAnnotationParameterList parameterList =
                annotation.getParameterList();
        if (parameterList.getChildren().length == 0) {
            return true;
        }
        final PsiNameValuePair[] attributes = parameterList.getAttributes();
        if (attributes.length != 1) {
            return false;
        }
        final PsiNameValuePair attribute = attributes[0];
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value == null) {
            return false;
        }
        final String name = attribute.getName();
        return name == null;
    }
}
