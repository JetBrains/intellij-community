// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.siyeh.ig.junit.JUnitCommonClassNames

internal fun processArrayInAnnotationParameter(attributeValue: PsiAnnotationMemberValue?,
                                               checker: (value: PsiAnnotationMemberValue) -> Unit) {
  if (attributeValue is PsiLiteral || attributeValue is PsiClassObjectAccessExpression) {
    checker.invoke(attributeValue)
  }
  else if (attributeValue is PsiArrayInitializerMemberValue) {
    for (memberValue in attributeValue.initializers) {
      processArrayInAnnotationParameter(memberValue, checker)
    }
  }
}

internal fun hasMultipleParameters(method: PsiMethod): Boolean {
  val containingClass = method.containingClass
  return containingClass != null &&
         method.parameterList.parameters
           .filter {
             !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INFO) &&
             !InheritanceUtil.isInheritor(it.type, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_REPORTER)
           }
           .count() > 1
         && !MetaAnnotationUtil.isMetaAnnotated(method, JUnit5MalformedParameterizedInspection.Annotations.EXTENDS_WITH)
         && !MetaAnnotationUtil.isMetaAnnotatedInHierarchy(containingClass, JUnit5MalformedParameterizedInspection.Annotations.EXTENDS_WITH)
}

internal fun getElementToHighlight(attributeValue: PsiElement,
                                  method: PsiMethod,
                                  default: PsiNameIdentifierOwner = method): PsiElement =
  if (PsiTreeUtil.isAncestor(method, attributeValue, true)) attributeValue else default.nameIdentifier ?: default
