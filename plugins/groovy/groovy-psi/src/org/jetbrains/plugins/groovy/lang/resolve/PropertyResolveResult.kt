// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable
import org.jetbrains.plugins.groovy.lang.resolve.processors.SubstitutorComputer

class PropertyResolveResult(
  element: PsiMethod,
  place: PsiElement,
  resolveContext: PsiElement?,
  partialSubstitutor: PsiSubstitutor,
  substitutorComputer: SubstitutorComputer,
  argumentTypes: Array<PsiType?>?,
  private val spreadState: SpreadState?
) : BaseGroovyResolveResult<PsiMethod>(element, place, resolveContext) {

  private val fullSubstitutor by lazy {
    substitutorComputer.obtainSubstitutor(partialSubstitutor, element, resolveContext)
  }

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val applicability by lazy {
    isApplicable(argumentTypes, element, substitutor, place, true)
  }

  override fun isApplicable(): Boolean = applicability

  override fun getSpreadState(): SpreadState? = spreadState

  override fun isInvokedOnProperty(): Boolean = true
}
