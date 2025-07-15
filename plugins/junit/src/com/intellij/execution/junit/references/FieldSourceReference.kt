// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.siyeh.ig.psiutils.TestUtils

class FieldSourceReference(element: PsiLanguageInjectionHost) : JUnitFieldAnnotationReference(element) {
  override fun hasNoStaticProblem(element: PsiField, literalClazz: PsiClass, literalMethod: PsiMethod?): Boolean {
    val isStatic = element.hasModifierProperty(PsiModifier.STATIC)
    val containingClass = element.containingClass ?: return false

    val perClass = TestUtils.testInstancePerClass(containingClass)
    val isSameClass = containingClass == literalClazz

    return isStatic || (perClass && isSameClass)
  }
}