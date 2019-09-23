// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.util.PsiUtil.captureToplevelWildcards
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class TypeConstraint(
  private val leftType: PsiType,
  private val rightType: PsiType?,
  private val context: PsiElement
) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    var argType = rightType ?: PsiType.NULL
    if (argType is GrTupleType) {
      argType = TypesUtil.rawWildcard(argType, context) ?: argType
    }
    if (argType !is GrLiteralClassType) {
      argType = captureToplevelWildcards(argType, context)
    }
    if (argType is GrMapType && session.skipClosureBlock) {
      argType = TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, context)
    }

    val t = session.contextSubstitutor.substitute(session.substituteWithInferenceVariables(leftType))
    val s = session.contextSubstitutor.substitute(session.substituteWithInferenceVariables(argType))

    constraints.add(TypeCompatibilityConstraint(t, s))
    return true
  }
}
