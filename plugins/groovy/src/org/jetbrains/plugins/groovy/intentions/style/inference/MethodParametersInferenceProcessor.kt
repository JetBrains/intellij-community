// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode.Companion.InstantiationHint
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * Allows to infer method parameters types regarding method calls and inner dependencies between types.
 */

/**
 * Performs full substitution for non-typed parameters of [InferenceDriver.virtualMethod]
 * Inference is performed in 3 phases:
 * 1. Creating a type parameter for each of existing non-typed parameters
 * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
 * 3. Inferring dependencies between new type parameters and instantiating them.
 */
fun runInferenceProcess(method: GrMethod): GrMethod {
  val driver = InferenceDriver(method)
  if (!driver.treatedAsOverriddenMethod()) {
    driver.setUpNewTypeParameters()
    val signatureSubstitutor = setUpParametersSignature(driver)
    if (driver.method.isConstructor) {
      driver.acceptFinalSubstitutor(signatureSubstitutor)
    }
    else {
      driver.parametrizeMethod(signatureSubstitutor)
      val graph = setUpGraph(driver)
      inferTypeParameters(graph, driver)
    }
  }
  return driver.virtualMethod
}

private fun setUpParametersSignature(driver: InferenceDriver): PsiSubstitutor {
  val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod,
                                                          driver.virtualParametersMapping)
  driver.collectOuterCalls(inferenceSession)
  return inferenceSession.inferSubst()
}


private fun setUpGraph(driver: InferenceDriver): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod)
  val initialVariables = driver.virtualMethod.typeParameters
  driver.collectInnerMethodCalls(inferenceSession)
  driver.constantTypes.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
  inferenceSession.run { repeatInferencePhases(); infer() }
  val inferenceVariables = driver.virtualMethod.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }
  return createGraphFromInferenceVariables(inferenceVariables, inferenceSession, driver, initialVariables)
}

private fun inferTypeParameters(initialGraph: InferenceUnitGraph,
                                driver: InferenceDriver) {
  val inferredGraph = determineDependencies(initialGraph)
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  for (unit in inferredGraph.resolveOrder()) {
    val (instantiation, hint) = unit.smartTypeInstantiation()
    val transformedType = when (hint) {
      InstantiationHint.NEW_TYPE_PARAMETER -> driver.createBoundedTypeParameter(unit.type.name, resultSubstitutor,
                                                                                resultSubstitutor.substitute(instantiation)).type()
      InstantiationHint.REIFIED_AS_PROPER_TYPE -> resultSubstitutor.substitute(instantiation)
      InstantiationHint.ENDPOINT_TYPE_PARAMETER -> {
        endpoints.add(unit)
        instantiation
      }
      InstantiationHint.REIFIED_AS_TYPE_PARAMETER -> {
        driver.finalTypeParameterList.add(unit.core.initialTypeParameter)
        instantiation
      }
      InstantiationHint.BOUNDED_WILDCARD -> resultSubstitutor.substitute(
        PsiWildcardType.createExtends(driver.virtualMethod.manager, instantiation))
      InstantiationHint.UNBOUNDED_WILDCARD -> resultSubstitutor.substitute(PsiWildcardType.createUnbounded(driver.virtualMethod.manager))
    }
    if (hint != InstantiationHint.ENDPOINT_TYPE_PARAMETER) {
      resultSubstitutor = resultSubstitutor.put(unit.core.initialTypeParameter, transformedType)
    }
  }
  val endpointTypes = endpoints.map {
    val completelySubstitutedType = resultSubstitutor.recursiveSubstitute(it.typeInstantiation)
    driver.createBoundedTypeParameter(it.type.name, resultSubstitutor, completelySubstitutedType).type()
  }
  val endpointSubstitutor = PsiSubstitutor.EMPTY.putAll(endpoints.map { it.core.initialTypeParameter }.toTypedArray(),
                                                        endpointTypes.toTypedArray())
  driver.acceptFinalSubstitutor(resultSubstitutor.putAll(endpointSubstitutor))
}
