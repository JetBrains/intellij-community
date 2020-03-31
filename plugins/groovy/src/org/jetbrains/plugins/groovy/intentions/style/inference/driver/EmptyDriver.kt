// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeMapper
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

object EmptyDriver : InferenceDriver {
  override fun typeParameters(): Collection<PsiTypeParameter> = emptyList()

  override fun collectInnerConstraints(): TypeUsageInformation = TypeUsageInformation.EMPTY

  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): InferenceDriver = this

  override fun instantiate(resultMethod: GrMethod) {}

  override fun acceptTypeVisitor(visitor: PsiTypeMapper, resultMethod: GrMethod) = this

  override fun collectOuterConstraints(): Collection<ConstraintFormula> = emptyList()

  override fun collectSignatureSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY
}