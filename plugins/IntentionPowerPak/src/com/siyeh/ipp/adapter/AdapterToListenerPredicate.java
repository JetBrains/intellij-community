/*
 * Copyright 2009-2013 Bas Leijdekkers
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
package com.siyeh.ipp.adapter;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class AdapterToListenerPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return false;
    }
    final PsiReferenceList referenceList = (PsiReferenceList)parent;
    if (PsiReferenceList.Role.EXTENDS_LIST != referenceList.getRole()) {
      return false;
    }
    final PsiElement grandParent = referenceList.getParent();
    if (!(grandParent instanceof PsiClass)) {
      return false;
    }
    final PsiJavaCodeReferenceElement[] referenceElements =
      referenceList.getReferenceElements();
    if (referenceElements.length != 1) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement =
      referenceElements[0];
    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)target;
    @NonNls final String className = aClass.getName();
    if (!className.endsWith("Adapter")) {
      return false;
    }
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList == null) {
      return false;
    }
    final PsiJavaCodeReferenceElement[] implementsReferences = implementsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
      @NonNls final String name = implementsReference.getReferenceName();
      if (name == null || !name.endsWith("Listener")) {
        continue;
      }
      final PsiElement implementsTarget = implementsReference.resolve();
      if (!(implementsTarget instanceof PsiClass)) {
        continue;
      }
      final PsiClass implementsClass = (PsiClass)implementsTarget;
      if (!implementsClass.isInterface()) {
        continue;
      }
      return true;
    }
    return false;
  }
}