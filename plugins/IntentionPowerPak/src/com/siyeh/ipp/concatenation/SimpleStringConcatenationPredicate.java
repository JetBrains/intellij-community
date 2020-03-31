/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class SimpleStringConcatenationPredicate implements PsiElementPredicate {

  private final boolean excludeConcatenationsInsideAnnotations;

  SimpleStringConcatenationPredicate(boolean excludeConcatenationsInsideAnnotations) {
    this.excludeConcatenationsInsideAnnotations = excludeConcatenationsInsideAnnotations;
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!ExpressionUtils.isStringConcatenation(element)) {
      return false;
    }
    return !(excludeConcatenationsInsideAnnotations && (AnnotationUtil.isInsideAnnotation(element) || 
                                                        PsiTreeUtil.getParentOfType(element, PsiAnnotationMethod.class) != null));
  }
}
