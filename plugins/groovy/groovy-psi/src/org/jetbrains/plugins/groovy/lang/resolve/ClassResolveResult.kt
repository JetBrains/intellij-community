// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession

class ClassResolveResult(
  clazz: PsiClass,
  place: PsiElement,
  state: ResolveState,
  typeArguments: Array<out PsiType>?
) : BaseGroovyResolveResult<PsiClass>(clazz, place, state) {

  private val shouldInfer = typeArguments != null && typeArguments.isEmpty()

  val contextSubstitutor by lazy {
    if (shouldInfer) {
      super.getSubstitutor()
    }
    else {
      super.getSubstitutor().putAll(element, typeArguments)
    }
  }

  override fun getSubstitutor(): PsiSubstitutor = if (shouldInfer) {
    inferredSubstitutor
  }
  else {
    contextSubstitutor
  }

  private val inferredSubstitutor: PsiSubstitutor by lazyPub {
    RecursionManager.doPreventingRecursion(this, false) {
      buildTopLevelSession(place).inferSubst(this)
    } ?: error("Recursion prevented")
  }
}
