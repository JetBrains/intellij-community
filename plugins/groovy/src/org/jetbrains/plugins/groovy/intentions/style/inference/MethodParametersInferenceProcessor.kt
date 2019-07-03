// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * Allows to infer method parameters types regarding method calls and inner dependencies between types.
 */
class MethodParametersInferenceProcessor(method: GrMethod) {
  private val driver: InferenceDriver = InferenceDriver(method)

  /**
   * Performs full substitution for non-typed parameters of [InferenceDriver.virtualMethod]
   * Inference is performed in 3 phases:
   * 1. Creating a type parameter for each of existing non-typed parameter
   * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
   * 3. Inferring dependencies between new type parameters and instantiating them.
   */
  fun runInferenceProcess(): GrMethod {
    driver.setUpNewTypeParameters()
    setUpParametersSignature()
    val graph = setUpGraph()
    inferTypeParameters(graph)
    return driver.virtualMethod
  }

  private fun setUpParametersSignature() {
    val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod,
                                                            driver.virtualParametersMapping)
    driver.collectOuterCalls(inferenceSession)
    val signatureSubstitutor = inferenceSession.inferSubst()
    driver.parametrizeMethod(signatureSubstitutor)
  }


  private fun setUpGraph(): InferenceUnitGraph {
    val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod)
    driver.collectInnerMethodCalls(inferenceSession)
    driver.constantTypes.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
    inferenceSession.run { repeatInferencePhases(); infer() }
    val inferenceVariables = driver.virtualMethod.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }
    return createGraphFromInferenceVariables(inferenceVariables, inferenceSession, driver)
  }

  private fun inferTypeParameters(initialGraph: InferenceUnitGraph) {
    val inferredGraph = determineDependencies(initialGraph)
    var resultSubstitutor = PsiSubstitutor.EMPTY
    val endpoints = mutableSetOf<InferenceUnitNode>()
    for (unit in inferredGraph.resolveOrder()) {
      val preferableType = getPreferableType(unit, resultSubstitutor, endpoints)
      if (unit !in endpoints) {
        resultSubstitutor = resultSubstitutor.put(unit.core.initialTypeParameter, preferableType)
      }
    }
    val endpointTypes = endpoints.map {
      val completelySubstitutedType = resultSubstitutor.recursiveSubstitute(it.typeInstantiation)
      driver.createBoundedTypeParameterElement(it.type.name, resultSubstitutor, completelySubstitutedType).type()
    }
    val endpointSubstitutor = PsiSubstitutor.EMPTY.putAll(endpoints.map { it.core.initialTypeParameter }.toTypedArray(),
                                                          endpointTypes.toTypedArray())
    driver.acceptFinalSubstitutor(resultSubstitutor.putAll(endpointSubstitutor))
  }

  private fun getPreferableType(unit: InferenceUnitNode,
                                resultSubstitutor: PsiSubstitutor, endpoints: MutableSet<InferenceUnitNode>): PsiType {
    val mayBeDirectlyInstantiated =
      !unit.forbidInstantiation &&
      when {
        unit.core.flexible -> (unit.typeInstantiation !is PsiIntersectionType)
        else -> unit.subtypes.size <= 1
      }
    when {
      mayBeDirectlyInstantiated -> {
        val instantiation = when {
          unit.core.flexible || unit.subtypes.isNotEmpty() || unit.direct -> unit.typeInstantiation
          unit.typeInstantiation == unit.type || unit.typeInstantiation.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ->
            PsiWildcardType.createUnbounded(driver.virtualMethod.manager)
          else -> PsiWildcardType.createExtends(driver.virtualMethod.manager, unit.typeInstantiation)
        }
        return resultSubstitutor.substitute(instantiation)
      }
      else -> {
        val advice = unit.parent?.type ?: unit.typeInstantiation
        if (advice == unit.typeInstantiation) {
          endpoints.add(unit)
          return unit.type
        }
        else {
          val newTypeParameter = driver.createBoundedTypeParameterElement(unit.type.name, resultSubstitutor, advice)
          return newTypeParameter.type()
        }
      }
    }
  }
}