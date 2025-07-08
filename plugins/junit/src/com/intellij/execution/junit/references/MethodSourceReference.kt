// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class MethodSourceReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference(element) {
  override fun hasNoStaticProblem(factoryMethod: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val isStatic = factoryMethod.hasModifierProperty(PsiModifier.STATIC)
    val psiClass = factoryMethod.containingClass ?: return false
    return factoryMethod.parameterList.isEmpty && TestUtils.testInstancePerClass(psiClass) != isStatic
  }
}
