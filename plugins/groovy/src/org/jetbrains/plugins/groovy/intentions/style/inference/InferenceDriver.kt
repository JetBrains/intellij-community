// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession

interface InferenceDriver {

  fun setUpNewTypeParameters()

  fun collectOuterCalls(session: GroovyInferenceSession)

  fun parametrizeMethod(signatureSubstitutor: PsiSubstitutor)

  fun collectInnerMethodCalls(inferenceSession: GroovyInferenceSession)

  val constantTypes: List<PsiType>

  val flexibleTypes: List<PsiType>

  val forbiddingTypes: List<PsiType>

  fun acceptFinalSubstitutor(resultSubstitutor: PsiSubstitutor)

  fun createBoundedTypeParameterElement(name: String, representativeSubstitutor: PsiSubstitutor, advice: PsiType?): PsiTypeParameter
}