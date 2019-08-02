// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod


data class BoundConstraint(val clazz: PsiClass, val marker: ContainMarker) {
  /**
  Marker represents relation between [clazz] and some type parameter
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

data class TypeUsageInformation(val contravariantTypes: Set<PsiType>,
                                val requiredClassTypes: Map<PsiTypeParameter, List<BoundConstraint>>,
                                val constraints: Collection<ConstraintFormula>,
                                val covariantTypes: Set<PsiType> = emptySet(),
                                val dependentTypes: Set<PsiTypeParameter> = emptySet()) {
  operator fun plus(typeUsageInformation: TypeUsageInformation): TypeUsageInformation {
    return merge(listOf(this, typeUsageInformation))
  }

  companion object {

    private fun <K, V> flattenMap(data: Iterable<Map<out K, List<V>>>): Map<K, List<V>> =
      data
        .flatMap { it.entries }
        .groupBy { it.key }
        .map { (key, values) -> key to values.flatMap { it.value } }
        .toMap()


    fun merge(data: Collection<TypeUsageInformation>): TypeUsageInformation {
      val contravariantTypes = data.flatMap { it.contravariantTypes }.toSet()
      val requiredClassTypes = flattenMap(data.map { it.requiredClassTypes })
      val constraints = data.flatMap { it.constraints }
      val covariantTypes = data.flatMap { it.covariantTypes }.toSet()
      val dependentTypes = data.flatMap { it.dependentTypes }.toSet()
      return TypeUsageInformation(contravariantTypes, requiredClassTypes, constraints, covariantTypes, dependentTypes)
    }
  }
}

fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()