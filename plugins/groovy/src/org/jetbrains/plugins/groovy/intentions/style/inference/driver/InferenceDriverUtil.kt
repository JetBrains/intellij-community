// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

/**
 * Reaches type parameter that does not extend other type parameter
 * @param type should be a type parameter
 */
tailrec fun extractEndpointType(type: PsiClassType, typeParameters: List<PsiType>): PsiClassType =
  if (type.superTypes.size == 1 && type.superTypes.single() in typeParameters) {
    extractEndpointType(type.superTypes.single() as PsiClassType, typeParameters)
  }
  else {
    type
  }


data class BoundConstraint(val clazz: PsiClass, val marker: ContainMarker) {
  // containing means the same as in jls-4.5.1
  enum class ContainMarker {
    EQUAL,
    CONTAINS,
    LOWER
  }
}

data class TypeUsageInformation(val contravariantTypes: Set<PsiType>,
                                val requiredClassTypes: Map<PsiTypeParameter, List<BoundConstraint>>,
                                val constraints: Collection<ConstraintFormula>,
                                val covariantTypes: Set<PsiType> = emptySet(),
                                val dependentTypes: Set<PsiTypeParameter> = emptySet(),
                                val inhabitedTypes: Map<PsiTypeParameter, List<PsiClass>> = emptyMap()) {
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
      val inhabitedTypes = flattenMap(data.map { it.inhabitedTypes })
      return TypeUsageInformation(contravariantTypes, requiredClassTypes, constraints, covariantTypes, dependentTypes,
                                  inhabitedTypes)
    }
  }
}

fun setUpParameterMapping(sourceMethod: GrMethod, sinkMethod: GrMethod) = sourceMethod.parameters.zip(sinkMethod.parameters).toMap()