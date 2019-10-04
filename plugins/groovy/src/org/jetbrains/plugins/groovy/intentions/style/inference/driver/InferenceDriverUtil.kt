// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.impl.GdkMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import kotlin.LazyThreadSafetyMode.NONE


data class BoundConstraint(val type: PsiType, val marker: ContainMarker) {
  /**
  Marker represents relation between [type] and some type parameter
  Aside from intuitive relations [EQUAL], [UPPER] and [LOWER], here is also an [INHABIT] one.

  Example:
  `def <T> void foo(List<T> list) { list.add(1 as Integer) }`
  `foo([1] as List<Number>)`
  `foo([1] as List<Serializable>)`

  So [Number] and [Serializable] are types that can inhabit `T` (and also its lower bounds) and [Integer] is a type that only a lower bound.
  It allows us to let `T` be a `? super Number`
   */
  enum class ContainMarker {
    EQUAL,
    UPPER,
    LOWER,
    INHABIT
  }
}

data class TypeUsageInformation(val requiredClassTypes: Map<PsiTypeParameter, List<BoundConstraint>>,
                                val constraints: Collection<ConstraintFormula>,
                                val dependentTypes: Set<PsiTypeParameter> = emptySet()) {
  operator fun plus(typeUsageInformation: TypeUsageInformation): TypeUsageInformation {
    return merge(listOf(this, typeUsageInformation))
  }

  val contravariantTypes: List<PsiType> by lazy(NONE) {
    requiredClassTypes.mapNotNull { (typeParameter, bounds) ->
      if (bounds.all { it.marker != UPPER } && bounds.count { it.marker == LOWER } > 0) {
        typeParameter.type()
      }
      else {
        null
      }
    }
  }

  val invariantTypes: List<PsiType> by lazy(NONE) {
    requiredClassTypes.filter { (_, bounds) ->
      bounds.all { it.marker == INHABIT }
    }.map { it.key.type() }
  }

  val covariantTypes: List<PsiType> by lazy(NONE) {
    requiredClassTypes.mapNotNull { (typeParameter, bounds) ->
      val inhabitTypes = bounds.filter { it.marker == INHABIT }.toSet()
      if (bounds.all { it.marker != LOWER } && inhabitTypes.size > 1 && bounds.count { it.marker == UPPER } > 0) {
        typeParameter.type()
      }
      else {
        null
      }
    }
  }

  companion object {

    private fun <K, V> flattenMap(data: Iterable<Map<out K, List<V>>>): Map<K, List<V>> =
      data
        .flatMap { it.entries }
        .groupBy { it.key }
        .map { (key, values) -> key to values.flatMap { it.value } }
        .toMap()


    fun merge(data: Collection<TypeUsageInformation>): TypeUsageInformation {
      val requiredClassTypes = flattenMap(data.map { it.requiredClassTypes })
      val constraints = data.flatMap { it.constraints }
      val dependentTypes = data.flatMap { it.dependentTypes }.toSet()
      return TypeUsageInformation(requiredClassTypes, constraints, dependentTypes)
    }
  }
}

fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()

fun getJavaLangObject(context: PsiElement): PsiClassType {
  return PsiType.getJavaLangObject(context.manager, context.resolveScope)
}

fun GroovyMethodCandidate.smartReceiver(): PsiType? =
  when (this) {
    is GdkMethodCandidate -> argumentMapping?.arguments?.first()?.type
    else -> receiver
  }


fun GroovyMethodCandidate.smartContainingType(): PsiType? =
  when (this) {
    is GdkMethodCandidate -> (method.parameters.first()?.type as PsiType)
    else -> method.containingClass?.type()
  }.takeIf { it.resolve() !is PsiTypeParameter }
