/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ipp.types;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class DiamondTypePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiReferenceParameterList)) {
      return false;
    }
    final PsiReferenceParameterList referenceParameterList =
      (PsiReferenceParameterList)element;
    final PsiTypeElement[] typeParameterElements =
      referenceParameterList.getTypeParameterElements();
    if (typeParameterElements.length != 1) {
      return false;
    }
    final PsiTypeElement typeParameterElement = typeParameterElements[0];
    final PsiType type = typeParameterElement.getType();
    return type instanceof PsiDiamondType;
  }
}
