// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyOverloadResolver
import org.jetbrains.plugins.groovy.lang.resolve.impl.PositionalArgumentMapping

/**
 * Methods accepting `org.gradle.api.Action` win overload resolution with any other SAM interface.
 */
class GradleActionOverloadResolver : GroovyOverloadResolver {
  override fun compare(left: GroovyMethodResult, right: GroovyMethodResult): Int {
    val leftTypes = left.parameterTypes ?: return 0
    val rightTypes = right.parameterTypes ?: return 0
    return positionalParametersDistance(leftTypes, rightTypes)
  }
}

private val GroovyMethodResult.parameterTypes: List<PsiType>?
  get() = candidate?.argumentMapping.asSafely<PositionalArgumentMapping<*>>()?.expectedTypes?.map { it.first }

private fun positionalParametersDistance(leftTypes: List<PsiType>, rightTypes: List<PsiType>): Int {
  var result = 0
  for ((leftType, rightType) in leftTypes.zip(rightTypes)) {
    val comparisonResult = compareTypes(leftType, rightType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE, GradleCommonClassNames.GRADLE_API_ACTION)
    if (comparisonResult != 0) {
      // rightParameterType must be SAM-equivalent to `Action`
      if (result == 0) result = comparisonResult else if (result != comparisonResult) return 0
    } else if (leftType != rightType) {
      // If there is an ambiguity in non-gradle-related parameters, then we fail to perform the overload resolution
      return 0
    }
  }
  return result
}

private fun compareTypes(leftType: PsiType, rightType: PsiType, vararg fqns: String) : Int {
  val leftFqn = leftType.asSafely<PsiClassType>()?.resolve()?.qualifiedName
  val rightFqn = rightType.asSafely<PsiClassType>()?.resolve()?.qualifiedName
  for (fqn in fqns) {
    if (fqn == leftFqn) return -1
    if (fqn == rightFqn) return 1
  }
  return 0
}