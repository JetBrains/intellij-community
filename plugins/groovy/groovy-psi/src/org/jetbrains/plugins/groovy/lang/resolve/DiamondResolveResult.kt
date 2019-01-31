// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession
import org.jetbrains.plugins.groovy.util.recursionPreventingLazy

class DiamondResolveResult(
  clazz: PsiClass,
  place: PsiElement,
  state: ResolveState
) : BaseGroovyResolveResult<PsiClass>(clazz, place, state) {

  override fun getSubstitutor(): PsiSubstitutor = inferredSubstitutor ?: run {
    log.warn("Recursion prevented")
    PsiSubstitutor.EMPTY
  }

  private val inferredSubstitutor: PsiSubstitutor? by recursionPreventingLazy {
    buildTopLevelSession(place).inferSubst(this)
  }
}
