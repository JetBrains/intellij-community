// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * @author knisht
 */

/**
 * Allows to infer method parameters types regarding method calls and inner dependencies between types.
 */
class MethodParametersInferenceProcessor(private val method: GrMethod, private val driver: InferenceDriver = InferenceDriverImpl(method)) {

  /**
   * Performs full substitution for non-typed parameters of [method]
   * Inference is performed in 3 phases:
   * 1. Creating a type parameter for each of existing non-typed parameter
   * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
   * 3. Inferring dependencies between new type parameters and instantiating them.
   */
  fun runInferenceProcess() {
    driver.setUpNewTypeParameters()
    setUpParametersSignature()
    val registry = setUpRegistry()
    inferTypeParameters(registry)
  }

  private fun setUpParametersSignature() {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    driver.collectOuterCalls(inferenceSession)
    val signatureSubstitutor = inferenceSession.inferSubst()
    driver.parametrizeMethod(signatureSubstitutor)
  }


  private fun setUpRegistry(): InferenceUnitRegistry {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    driver.collectInnerMethodCalls(inferenceSession)
    driver.constantTypes.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
    inferenceSession.run { repeatInferencePhases(); infer() }
    val inferenceVariables = method.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }
    return InferenceUnitRegistry().apply {
      setUpUnits(inferenceVariables, inferenceSession)
      driver.constantTypes.forEach { searchUnit(it)?.constant = true }
      driver.flexibleTypes.forEach { searchUnit(it)?.flexible = true }
      driver.forbiddingTypes.mapNotNull { searchUnit(it) }
        .filter { it.typeInstantiation == PsiType.getJavaLangObject(method.manager, method.resolveScope) }
        .forEach { it.forbidInstantiation = true }
    }
  }

  private fun inferTypeParameters(registry: InferenceUnitRegistry) {
    val graph = InferenceUnitGraph(registry)
    val representativeSubstitutor = collectRepresentativeSubstitutor(graph, registry)
    var resultSubstitutor = PsiSubstitutor.EMPTY
    for (unit in graph.getRepresentatives().filter { !it.constant }) {
      val preferableType = getPreferableType(graph, unit, representativeSubstitutor, resultSubstitutor)
      graph.getEqualUnits(unit).forEach { resultSubstitutor = resultSubstitutor.put(it.initialTypeParameter, preferableType) }
    }
    driver.acceptFinalSubstitutor(resultSubstitutor)
  }

  private fun getPreferableType(graph: InferenceUnitGraph,
                                unit: InferenceUnit,
                                representativeSubstitutor: PsiSubstitutor,
                                resultSubstitutor: PsiSubstitutor): PsiType {
    val equalTypeParameters = graph.getEqualUnits(unit).filter { it.typeInstantiation == PsiType.NULL }
    val mayBeDirectlyInstantiated = equalTypeParameters.isEmpty() &&
                                    when {
                                      unit.flexible -> (unit.typeInstantiation !is PsiIntersectionType)
                                      else -> !unit.forbidInstantiation && unit.subtypes.size <= 1
                                    }
    when {
      mayBeDirectlyInstantiated -> {
        val instantiation = when {
          unit.flexible || unit.subtypes.size != 0 -> unit.typeInstantiation
          unit.typeInstantiation == unit.type -> PsiWildcardType.createUnbounded(method.manager)
          else -> PsiWildcardType.createExtends(method.manager, unit.typeInstantiation)
        }
        return resultSubstitutor.substitute(representativeSubstitutor.substitute(instantiation))
      }
      else -> {
        val parent = unit.unitInstantiation
        val advice = parent?.type ?: graph.initialInstantiations[unit]!!
        val newTypeParam = driver.createBoundedTypeParameterElement(unit.initialTypeParameter.name!!, representativeSubstitutor,
                                                                    resultSubstitutor,
                                                                    advice)
        return newTypeParam.type()
      }
    }
  }
}