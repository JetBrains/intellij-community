// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraphBuilder
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * @author knisht
 */

/**
 * Allows to infer method parameters types regarding method calls and inner dependencies between types.
 */
class MethodParametersInferenceProcessor(private val method: GrMethod, private val driver: InferenceDriver = InferenceDriver(method)) {

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
    val graph = setUpGraph()
    inferTypeParameters(graph)
  }

  private fun setUpParametersSignature() {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    driver.collectOuterCalls(inferenceSession)
    val signatureSubstitutor = inferenceSession.inferSubst()
    driver.parametrizeMethod(signatureSubstitutor)
  }


  private fun setUpGraph(): InferenceUnitGraph {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    driver.collectInnerMethodCalls(inferenceSession)
    driver.constantTypes.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
    inferenceSession.run { repeatInferencePhases(); infer() }
    val inferenceVariables = method.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }
    return InferenceUnitGraphBuilder.createGraphFromInferenceVariables(inferenceVariables, inferenceSession, driver.flexibleTypes.toSet(),
                                                                       driver.constantTypes.toSet(),
                                                                       driver.forbiddingTypes.toSet(),
                                                                       driver.appearedClassTypes)
  }

  private fun inferTypeParameters(initialGraph: InferenceUnitGraph) {
    val inferredGraph = determineDependencies(initialGraph)
    var resultSubstitutor = PsiSubstitutor.EMPTY
    for (unit in inferredGraph.resolveOrder()) {
      val preferableType = getPreferableType(unit, resultSubstitutor)
      resultSubstitutor = resultSubstitutor.put(unit.core.initialTypeParameter, preferableType)
    }
    driver.acceptFinalSubstitutor(resultSubstitutor)
  }

  private fun getPreferableType(unit: InferenceUnitNode,
                                resultSubstitutor: PsiSubstitutor): PsiType {
    val mayBeDirectlyInstantiated = !unit.forbidInstantiation &&
                                    when {
                                      unit.core.flexible -> (unit.typeInstantiation !is PsiIntersectionType)
                                      else -> unit.subtypes.size <= 1
                                    }
    when {
      mayBeDirectlyInstantiated -> {
        val instantiation = when {
          unit.core.flexible || unit.subtypes.isNotEmpty() || unit.direct -> unit.typeInstantiation
          unit.typeInstantiation == unit.type || unit.typeInstantiation.equalsToText("java.lang.Object") -> PsiWildcardType.createUnbounded(
            method.manager)
          else -> PsiWildcardType.createExtends(method.manager, unit.typeInstantiation)
        }
        return resultSubstitutor.substitute(instantiation)
      }
      else -> {
        val parent = unit.parent
        val advice = parent?.type ?: unit.typeInstantiation
        val newTypeParam = driver.createBoundedTypeParameterElement(unit.core.initialTypeParameter.name!!,
                                                                    resultSubstitutor,
                                                                    advice)
        return newTypeParam.type()
      }
    }
  }
}