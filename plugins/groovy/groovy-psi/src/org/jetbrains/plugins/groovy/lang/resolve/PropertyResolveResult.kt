// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable
import org.jetbrains.plugins.groovy.lang.resolve.processors.SubstitutorComputer

class PropertyResolveResult(
  element: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  substitutorComputer: SubstitutorComputer,
  argumentTypes: Array<PsiType?>?
) : BaseGroovyResolveResult<PsiMethod>(element, place, state) {

  private val fullSubstitutor by lazy {
    substitutorComputer.obtainSubstitutor(super.getSubstitutor(), element, currentFileResolveContext)
  }

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val applicability by lazy {
    isApplicable(argumentTypes, element, substitutor, place, true)
  }

  override fun isApplicable(): Boolean = applicability

  override fun isInvokedOnProperty(): Boolean = true
}
