// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.util.lazyPub

class ClassResolveResult(
  clazz: PsiClass,
  place: PsiElement,
  state: ResolveState,
  typeArguments: Array<out PsiType>?
) : BaseGroovyResolveResult<PsiClass>(clazz, place, state) {

  private val contextSubstitutor get() = super.getSubstitutor()

  override fun getSubstitutor(): PsiSubstitutor = mySubstitutor

  private val mySubstitutor: PsiSubstitutor by lazyPub(fun(): PsiSubstitutor {
    return if (typeArguments == null || typeArguments.isNotEmpty()) {
      contextSubstitutor.putAll(element, typeArguments)
    }
    else {
      contextSubstitutor
    }
  })
}
