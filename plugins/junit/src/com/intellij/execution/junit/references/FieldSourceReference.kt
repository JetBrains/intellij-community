// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiField
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiModifier
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class FieldSourceReference(element: PsiLanguageInjectionHost) : JUnitFieldAnnotationReference(element) {
  override fun hasNoStaticProblem(element: PsiField, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val isStatic = element.hasModifierProperty(PsiModifier.STATIC)
    val psiClass = element.containingClass ?: return false

    val perClass = TestUtils.testInstancePerClass(psiClass)
    val isSameClass = psiClass == literalClazz.javaPsi

    return isStatic || (perClass && isSameClass)
  }
}