// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession
import org.jetbrains.plugins.groovy.util.lazyPreventingRecursion

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

  private val inferredSubstitutor: PsiSubstitutor by lazyPreventingRecursion {
    buildTopLevelSession(place).inferSubst(this)
  }
}
