// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.*
import org.jetbrains.uast.*

internal class DisabledIfEnabledIfReference(element: PsiLanguageInjectionHost) : JUnitMethodAnnotationReference(element) {
  override fun hasNoStaticProblem(element: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val uMethodClass = element.containingClass?.toUElement(UClass::class.java) ?: return false
    val inExternalClazz = literalClazz != uMethodClass
    val atClassLevel = literalMethod == null
    val isStatic = element.hasModifierProperty(PsiModifier.STATIC)
    return !((inExternalClazz || atClassLevel) && !isStatic) && element.parameterList.isEmpty
  }
}