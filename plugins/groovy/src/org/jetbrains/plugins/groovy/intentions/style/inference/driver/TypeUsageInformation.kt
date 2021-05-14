// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.intentions.style.inference.CollectingGroovyInferenceSession
import org.jetbrains.plugins.groovy.intentions.style.inference.SignatureInferenceContext
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.addExpression
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class TypeUsageInformationBuilder(method: GrMethod, val signatureInferenceContext: SignatureInferenceContext) {
  private val requiredClassTypes: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>> = mutableMapOf()
  private val constraints: MutableList<ConstraintFormula> = mutableListOf()
  private val dependentTypes: MutableSet<PsiTypeParameter> = mutableSetOf()
  private val variableTypeParameters = method.typeParameters
  private val javaLangObject = getJavaLangObject(method)
  private val expressions: MutableList<GrExpression> = mutableListOf()

  fun generateRequiredTypes(typeParameter: PsiTypeParameter, type: PsiType, marker: BoundConstraint.ContainMarker) {
    val filteredType = signatureInferenceContext.filterType(type, typeParameter)
    if (filteredType == javaLangObject && marker == BoundConstraint.ContainMarker.UPPER) return
    val bindingTypes = expandWildcards(filteredType, typeParameter)
    bindingTypes.forEach { addRequiredType(typeParameter, BoundConstraint(it, marker)) }
  }

  fun addConstraint(constraintFormula: ConstraintFormula) {
    constraints.add(constraintFormula)
  }

  fun addConstrainingExpression(expression: GrExpression) {
    expressions.add(expression)
  }

  fun addDependentType(typeParameter: PsiTypeParameter) {
    dependentTypes.add(typeParameter)
  }

  fun build(): TypeUsageInformation = TypeUsageInformation(requiredClassTypes, constraints, dependentTypes, expressions)


  private fun addRequiredType(typeParameter: PsiTypeParameter, constraint: BoundConstraint) {
    if (typeParameter in variableTypeParameters && !constraint.type.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)) {
      val constraintTypeParameter = constraint.type.typeParameter()
      if (constraintTypeParameter != null && constraintTypeParameter in variableTypeParameters) {
        dependentTypes.add(typeParameter)
        dependentTypes.add(constraintTypeParameter)
      }
      else {
        requiredClassTypes.safePut(typeParameter, constraint)
      }
    }
  }

  private fun expandWildcards(type: PsiType, context: PsiElement): List<PsiType> =
    when (type) {
      is PsiWildcardType -> when {
        type.isSuper -> listOf(type.superBound, getJavaLangObject(context))
        type.isExtends -> listOf(type.extendsBound, PsiType.NULL)
        else -> listOf(javaLangObject, PsiType.NULL)
      }
      else -> listOf(type)
    }

  companion object {
    private fun <K, V> MutableMap<in K, MutableList<V>>.safePut(key: K, value: V) = computeIfAbsent(key) { mutableListOf() }.add(value)
  }
}

data class TypeUsageInformation(val requiredClassTypes: Map<PsiTypeParameter, List<BoundConstraint>>,
                                val constraints: Collection<ConstraintFormula>,
                                val dependentTypes: Set<PsiTypeParameter> = emptySet(),
                                val constrainingExpressions: List<GrExpression>) {

  operator fun plus(typeUsageInformation: TypeUsageInformation): TypeUsageInformation {
    return merge(listOf(this, typeUsageInformation))
  }

  fun fillSession(inferenceSession: CollectingGroovyInferenceSession) {
    constrainingExpressions.forEach { inferenceSession.addExpression(it) }
    constraints.forEach { inferenceSession.addConstraint(it) }
  }

  val contravariantTypes: List<PsiType> by lazy(LazyThreadSafetyMode.NONE) {
    requiredClassTypes.mapNotNull { (typeParameter, bounds) ->
      if (bounds.all { it.marker != BoundConstraint.ContainMarker.UPPER } && bounds.count { it.marker == BoundConstraint.ContainMarker.LOWER } > 0) {
        typeParameter.type()
      }
      else {
        null
      }
    }
  }

  val invariantTypes: List<PsiType> by lazy(LazyThreadSafetyMode.NONE) {
    requiredClassTypes.filter { (_, bounds) ->
      bounds.all { it.marker == BoundConstraint.ContainMarker.INHABIT }
    }.map { it.key.type() }
  }

  val covariantTypes: List<PsiType> by lazy(LazyThreadSafetyMode.NONE) {
    requiredClassTypes.mapNotNull { (typeParameter, bounds) ->
      val inhabitTypes = bounds.filter { it.marker == BoundConstraint.ContainMarker.INHABIT }.toSet()
      if (bounds.all { it.marker != BoundConstraint.ContainMarker.LOWER } && inhabitTypes.size > 1 && bounds.count { it.marker == BoundConstraint.ContainMarker.UPPER } > 0) {
        typeParameter.type()
      }
      else {
        null
      }
    }
  }

  companion object {

    val EMPTY = TypeUsageInformation(emptyMap(), emptyList(), emptySet(), emptyList())

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
      val constrainingExpressions = data.flatMap { it.constrainingExpressions }
      return TypeUsageInformation(requiredClassTypes, constraints, dependentTypes, constrainingExpressions)
    }
  }
}
