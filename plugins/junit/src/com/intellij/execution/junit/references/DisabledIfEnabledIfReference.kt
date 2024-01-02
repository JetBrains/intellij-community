// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.*

internal class DisabledIfEnabledIfReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference(element) {
  override fun hasNoStaticProblem(method: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val uMethodClass = method.containingClass?.toUElement(UClass::class.java) ?: return false
    val inExternalClazz = literalClazz != uMethodClass
    val atClassLevel = literalMethod == null
    val isStatic = method.hasModifierProperty(PsiModifier.STATIC)
    return !((inExternalClazz || atClassLevel) && !isStatic) && method.parameterList.isEmpty
  }

  object Provider : UastInjectionHostReferenceProvider() {
    override fun getReferencesForInjectionHost(
      uExpression: UExpression,
      host: PsiLanguageInjectionHost,
      context: ProcessingContext
    ): Array<PsiReference> = arrayOf(DisabledIfEnabledIfReference(host))
  }
}