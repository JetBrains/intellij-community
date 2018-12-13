// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.typing.EmptyListLiteralType
import org.jetbrains.plugins.groovy.lang.typing.EmptyMapLiteralType

class ListConstraint(private val leftType: PsiType?, private val literal: GrListOrMap) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    val type = literal.type
    if (type is EmptyListLiteralType || type is EmptyMapLiteralType) {
      // TODO consider adding separate interface for such cases
      val result = (type as? EmptyListLiteralType)?.resolveResult
                   ?: (type as? EmptyMapLiteralType)?.resolveResult
                   ?: return true
      val clazz = result.element
      val contextSubstitutor = result.contextSubstitutor
      require(contextSubstitutor === PsiSubstitutor.EMPTY)
      val typeParameters = clazz.typeParameters
      session.startNestedSession(typeParameters, contextSubstitutor, literal, result) { nested ->
        runNestedSession(nested, clazz)
      }
      return true
    }
    if (leftType != null) {
      constraints.add(TypeConstraint(leftType, type, literal))
    }
    return true
  }

  private fun runNestedSession(nested: GroovyInferenceSession, clazz: PsiClass) {
    if (leftType != null) {
      val left = nested.substituteWithInferenceVariables(leftType)
      val classType = nested.substituteWithInferenceVariables(clazz.type())
      nested.addConstraint(TypeCompatibilityConstraint(left, classType))
      nested.repeatInferencePhases()
    }
  }

  override fun toString(): String = "${leftType?.presentableText} <- ${literal.text}"
}
