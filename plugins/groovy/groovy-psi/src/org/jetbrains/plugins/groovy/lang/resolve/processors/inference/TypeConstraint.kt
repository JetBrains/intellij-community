// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class TypeConstraint(val leftType: PsiType, private val rightType: PsiType?, val context: PsiElement) : ConstraintFormula {
  override fun reduce(session: InferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    if (session !is GroovyInferenceSession) return true
    var argType = rightType ?: PsiType.NULL
    if (argType is GrTupleType) {
      val rawWildcardType = TypesUtil.rawWildcard(argType, context)
      argType = rawWildcardType ?: argType
    }

    if (argType !is GrClosureType)
      argType = com.intellij.psi.util.PsiUtil.captureToplevelWildcards(argType, context)

    val t = session.substituteWithInferenceVariables(leftType)
    val s = session.siteSubstitutor.substitute(session.substituteWithInferenceVariables(argType))

    constraints.add(TypeCompatibilityConstraint(t, s))
    return true
  }

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) {}
}
