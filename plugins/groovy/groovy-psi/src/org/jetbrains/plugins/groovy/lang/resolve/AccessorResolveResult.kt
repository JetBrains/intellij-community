// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.SubstitutorComputer

class AccessorResolveResult(
  element: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : BaseGroovyResolveResult<PsiMethod>(element, place, state) {

  private val receiverType = state[ClassHint.THIS_TYPE]

  private val fullSubstitutor by lazy {
    val computer = SubstitutorComputer(receiverType, PsiType.EMPTY_ARRAY, PsiType.EMPTY_ARRAY, place, place)
    computer.obtainSubstitutor(super.getSubstitutor(), element, currentFileResolveContext)
  }

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val applicability by lazy {
    val argumentTypes = arguments?.map { it.type }?.toTypedArray()
    isApplicable(argumentTypes, element, substitutor, place, true)
  }

  override fun isApplicable(): Boolean = applicability

  override fun isInvokedOnProperty(): Boolean = true
}
