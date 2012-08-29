/*
 * Copyright 2009-2012 Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertJUnit3TestCaseToJUnit4Predicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)parent;
    final PsiElement leftBrace = aClass.getLBrace();
    final int offsetInParent = element.getStartOffsetInParent();
    if (leftBrace == null || offsetInParent >= leftBrace.getStartOffsetInParent()) {
      return false;
    }
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList == null) {
      return false;
    }
    final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    if (referenceElements.length != 1) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement = referenceElements[0];
    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass)) {
      return false;
    }
    final PsiClass targetClass = (PsiClass)target;
    final String name = targetClass.getQualifiedName();
    if (!"junit.framework.TestCase".equals(name)) {
      return false;
    }
    final Project project = element.getProject();
    final GlobalSearchScope scope = element.getResolveScope();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass testAnnotation = psiFacade.findClass("org.junit.Test", scope);
    return testAnnotation != null;
  }
}
