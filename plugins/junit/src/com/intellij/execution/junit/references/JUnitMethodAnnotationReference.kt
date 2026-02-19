// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UMethod

abstract class JUnitMethodAnnotationReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference<PsiMethod, UMethod>(element) {
  override fun getPsiElementsByName(directClass: PsiClass, name: String, checkBases: Boolean): Array<PsiMethod> = directClass.findMethodsByName(prepareFactoryMethodName(name), checkBases)
  override fun uType(): Class<UMethod> = UMethod::class.java
  override fun toTypedPsiArray(collection: Collection<PsiMethod>): Array<PsiMethod> = collection.toTypedArray()
  override fun isPsiType(element: PsiElement): Boolean = element is PsiMethod
  override fun getAll(directClass: PsiClass): Array<PsiMethod> = directClass.allMethods

  private fun prepareFactoryMethodName(factoryMethodName: String): String {
    var result = factoryMethodName
    if (result.endsWith("()")) {
      result = result.substring(0, result.length - 2)
    }
    return result
  }
}