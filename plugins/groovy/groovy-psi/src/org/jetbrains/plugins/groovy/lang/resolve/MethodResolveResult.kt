// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import com.intellij.util.lazyPub
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession
import org.jetbrains.plugins.groovy.util.lazyPreventingRecursion
import kotlin.reflect.jvm.isAccessible

class MethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : BaseMethodResolveResult(method, place, state, arguments) {

  override fun getContextSubstitutor(): PsiSubstitutor = super.getSubstitutor()

  override fun getPartialSubstitutor(): PsiSubstitutor = myPartialSubstitutor

  private val myPartialSubstitutor by lazyPub {
    GroovyInferenceSessionBuilder(place, myCandidate, contextSubstitutor).build().inferSubst()
  }

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val fullSubstitutor by lazyPreventingRecursion {
    buildTopLevelSession(place).inferSubst(this)
  }

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
