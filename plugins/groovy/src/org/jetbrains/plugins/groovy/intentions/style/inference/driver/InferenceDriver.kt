// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.CollectingGroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

interface InferenceDriver {

  fun collectOuterConstraints(): Collection<ConstraintFormula>

  fun collectInnerConstraints(): TypeUsageInformation

  fun createParameterizedDriver(manager: ParameterizationManager,
                                parameterizedMethod: GrMethod,
                                substitutor: PsiSubstitutor): InferenceDriver

  fun instantiate(resultMethod: GrMethod, resultSubstitutor: PsiSubstitutor)

  fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod)

  fun forbiddingTypes(): List<PsiType> = emptyList()

  fun typeParameters() : Collection<PsiTypeParameter>

  fun collectSignatureSubstitutor() : PsiSubstitutor {
    val constraints = collectOuterConstraints()
    val session = CollectingGroovyInferenceSession(typeParameters().toTypedArray())
    constraints.forEach { session.addConstraint(it) }
    return session.inferSubst()
  }
}