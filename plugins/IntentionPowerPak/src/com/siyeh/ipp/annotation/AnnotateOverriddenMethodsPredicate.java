/*
 * Copyright 20112013 Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.Collection;

class AnnotateOverriddenMethodsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAnnotation)) {
      return false;
    }
    final PsiAnnotation annotation = (PsiAnnotation)element;
    final String annotationName = annotation.getQualifiedName();
    if (annotationName == null) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiModifierList)) {
      return false;
    }
    final PsiElement grandParent = parent.getParent();
    final PsiMethod method;
    final int parameterIndex;
    if (!(grandParent instanceof PsiMethod)) {
      if (!(grandParent instanceof PsiParameter)) {
        return false;
      }
      final PsiParameter parameter = (PsiParameter)grandParent;
      final PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList)) {
        return false;
      }
      final PsiParameterList parameterList =
        (PsiParameterList)greatGrandParent;
      parameterIndex = parameterList.getParameterIndex(parameter);
      final PsiElement greatGreatGrandParent =
        greatGrandParent.getParent();
      if (!(greatGreatGrandParent instanceof PsiMethod)) {
        return false;
      }
      method = (PsiMethod)greatGreatGrandParent;
    }
    else {
      parameterIndex = -1;
      method = (PsiMethod)grandParent;
    }
    final Project project = element.getProject();
    final Collection<PsiMethod> overridingMethods =
      OverridingMethodsSearch.search(method,
                                     GlobalSearchScope.allScope(project), true).findAll();
    if (overridingMethods.isEmpty()) {
      return false;
    }
    for (PsiMethod overridingMethod : overridingMethods) {
      if (parameterIndex == -1) {
        final PsiAnnotation foundAnnotation =
          AnnotationUtil.findAnnotation(overridingMethod,
                                        annotationName);
        if (foundAnnotation == null) {
          return true;
        }
      }
      else {
        final PsiParameterList parameterList =
          overridingMethod.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter = parameters[parameterIndex];
        final PsiAnnotation foundAnnotation =
          AnnotationUtil.findAnnotation(parameter,
                                        annotationName);
        if (foundAnnotation == null) {
          return true;
        }
      }
    }
    return false;
  }
}
