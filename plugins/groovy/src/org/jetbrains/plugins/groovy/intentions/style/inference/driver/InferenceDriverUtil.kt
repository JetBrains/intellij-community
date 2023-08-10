// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.impl.GdkMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


data class BoundConstraint(val type: PsiType, val marker: ContainMarker) {
  /**
  [ContainMarker] represents a relation between [type] and some type variable
  Possible relations and their meanings:
   * [EQUAL] -- type variable must be the same as [type]
   * [UPPER] -- type variable must be the subtype of [type] (not necessarily strict)
   * [LOWER] -- type variable must be the supertype of [type] (not necessarily strict)
   * [INHABIT] -- type variable must contain [type], i.e. to be the supertype of it or contravariant wildcard that is bounded by some subtype of [type]

  Example:
  `def <T> void foo(List<T> list) { list.add(1 as Integer) }`
  `foo([1] as List<Number>)`
  `foo([1] as List<Serializable>)`

  So [Number] and [Serializable] are the types that inhabit `T` (and also its lower bounds) and [Integer] is a type that is only a lower bound.
  It allows us to let `T` be a `? super Number`
   */
  enum class ContainMarker {
    EQUAL,
    UPPER,
    LOWER,
    INHABIT
  }
}

fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()

fun getJavaLangObject(context: PsiElement): PsiClassType {
  return PsiType.getJavaLangObject(context.manager, context.resolveScope)
}

fun GroovyMethodCandidate.smartReceiver(): PsiType? =
  when (this) {
    is GdkMethodCandidate -> argumentMapping?.arguments?.first()?.type
    else -> receiverType
  }


fun GroovyMethodCandidate.smartContainingType(): PsiType? =
  when (this) {
    is GdkMethodCandidate -> (method.parameters.first()?.type as PsiType)
    else -> method.containingClass?.type()
  }.takeIf { it.resolve() !is PsiTypeParameter }
