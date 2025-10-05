// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.*

internal class DisabledIfEnabledIfReference(element: PsiLanguageInjectionHost) : JUnitMethodAnnotationReference(element) {
  override fun hasNoStaticProblem(element: PsiMethod, literalClazz: PsiClass, literalMethod: PsiMethod?): Boolean {
    val methodClass = element.containingClass ?: return false
    val inExternalClazz = literalClazz != methodClass
    val atClassLevel = literalMethod == null
    val isStatic = element.hasModifierProperty(PsiModifier.STATIC)
    return !((inExternalClazz || atClassLevel) && !isStatic) && element.parameterList.isEmpty
  }
}