// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.annotation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.ig.JavaOverridingMethodUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

class AnnotateOverriddenMethodsPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAnnotation annotation)) {
      return false;
    }
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
      if (!(grandParent instanceof PsiParameter parameter)) {
        return false;
      }
      final PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList parameterList)) {
        return false;
      }
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

    String annotationShortName = StringUtil.getShortName(annotationName);
    Predicate<PsiMethod> preFilter = m -> {
      if (parameterIndex == -1) {
        return !JavaOverridingMethodUtil.containsAnnotationWithName(m, annotationShortName);
      }
      else {
        JvmParameter[] parameters = m.getParameters();
        if (parameters.length <= parameterIndex) {
          return false;
        }
        PsiModifierListOwner parameter = (PsiModifierListOwner)parameters[parameterIndex];
        return !JavaOverridingMethodUtil.containsAnnotationWithName(parameter, annotationShortName);
      }
    };
    Stream<PsiMethod> overridenMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, null, preFilter);
    // skip expensive check and just offer the intention when it might not be needed
    if (overridenMethods == null) return true;

    Iterator<PsiMethod> it = overridenMethods.iterator();
    while (it.hasNext()) {
      PsiMethod overridingMethod = it.next();
      if (parameterIndex == -1) {
        final PsiAnnotation foundAnnotation =
          AnnotationUtil.findAnnotation(overridingMethod, annotationName);
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
          AnnotationUtil.findAnnotation(parameter, annotationName);
        if (foundAnnotation == null) {
          return true;
        }
      }
    }
    return false;
  }
}
