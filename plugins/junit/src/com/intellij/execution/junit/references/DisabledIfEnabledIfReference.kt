// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

internal class DisabledIfEnabledIfReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference(element) {
  override fun hasNoStaticProblem(factoryMethod: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val uMethodClass = factoryMethod.containingClass?.toUElement(UClass::class.java) ?: return false
    val inExternalClazz = literalClazz != uMethodClass
    val atClassLevel = literalMethod == null
    val isStatic = factoryMethod.hasModifierProperty(PsiModifier.STATIC)
    return !((inExternalClazz || atClassLevel) && !isStatic) && factoryMethod.parameterList.isEmpty
  }
}