// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.annotations.NonNls

abstract class GrConstraintFormula : ConstraintFormula {

  final override fun reduce(session: InferenceSession?, constraints: MutableList<in ConstraintFormula>): Boolean {
    if (session !is GroovyInferenceSession) return true
    return reduce(session, constraints)
  }

  abstract fun reduce(session: GroovyInferenceSession, constraints: MutableList<in ConstraintFormula>): Boolean

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) = Unit

  @NonNls
  override fun toString(): String = super.toString()
}
