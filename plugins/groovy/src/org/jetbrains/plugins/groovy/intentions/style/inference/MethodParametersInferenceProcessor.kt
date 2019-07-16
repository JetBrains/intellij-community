// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
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
 * Performs full substitution for non-typed parameters of [method]
 * Inference is performed in 3 phases:
 * 1. Creating a type parameter for each of existing non-typed parameters
 * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
 * 3. Inferring dependencies between new type parameters and instantiating them.
 */
fun runInferenceProcess(method: GrMethod): GrMethod {
  val virtualMethod = createVirtualMethod(method)
  val overridableMethod = findOverridableMethod(method)
  if (overridableMethod != null) {
    copySignatureFrom(overridableMethod, virtualMethod)
  }
  else {
    val metaData = setUpNewTypeParameters(method, virtualMethod)
    val signatureSubstitutor = setUpParametersSignature(metaData)
    if (method.isConstructor) {
      acceptFinalSubstitutor(metaData,
                             signatureSubstitutor,
                             TypeParameterCollector(metaData.virtualMethod),
                             emptyList())
    }
    else {
      val constantParameters = parameterizeMethod(metaData, signatureSubstitutor)
      val graph = setUpGraph(metaData, constantParameters)
      inferTypeParameters(metaData, graph, constantParameters)
    }
  }
  return virtualMethod
}

private fun setUpParametersSignature(metaData: InferenceMetaData): PsiSubstitutor {
  val inferenceSession = CollectingGroovyInferenceSession(metaData.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, metaData.virtualMethod,
                                                          metaData.method.parameters.zip(
                                                            metaData.virtualMethod.parameters).map { (actual, virtual) -> actual.name to virtual }.toMap())
  collectOuterCalls(metaData.virtualMethod, metaData.closureParameters, metaData.method, inferenceSession)
  return inferenceSession.inferSubst()
}


private fun setUpGraph(metaData: InferenceMetaData,
                       constantParameters: List<PsiTypeParameter>): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(metaData.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, metaData.virtualMethod)
  val initialVariables = metaData.virtualMethod.typeParameters
  val typeUsage = collectInnerMethodCalls(metaData.virtualMethod, metaData.closureParameters, metaData.varargParameters, inferenceSession)
  constantParameters.map { it.type() }.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
  inferenceSession.infer()
  val inferenceVariables = metaData.virtualMethod.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }
  return createGraphFromInferenceVariables(inferenceVariables, inferenceSession, metaData.virtualMethod, initialVariables, typeUsage, constantParameters)
}

private fun inferTypeParameters(metaData: InferenceMetaData,
                                initialGraph: InferenceUnitGraph,
                                constantParameters: List<PsiTypeParameter>) {
  val inferredGraph = determineDependencies(initialGraph)
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  val collector = TypeParameterCollector(metaData.virtualMethod)
  for (unit in inferredGraph.resolveOrder()) {
    val (instantiation, hint) = unit.smartTypeInstantiation()
    val transformedType = when (hint) {
      InstantiationHint.NEW_TYPE_PARAMETER -> collector.createBoundedTypeParameter(unit.type.name, resultSubstitutor,
                                                                         resultSubstitutor.substitute(instantiation)).type()
      InstantiationHint.REIFIED_AS_PROPER_TYPE -> resultSubstitutor.substitute(instantiation)
      InstantiationHint.ENDPOINT_TYPE_PARAMETER -> {
        endpoints.add(unit)
        instantiation
      }
      InstantiationHint.REIFIED_AS_TYPE_PARAMETER -> {
        collector.typeParameterList.add(unit.core.initialTypeParameter)
        instantiation
      }
      InstantiationHint.BOUNDED_WILDCARD -> resultSubstitutor.substitute(
        PsiWildcardType.createExtends(metaData.virtualMethod.manager, instantiation))
      InstantiationHint.UNBOUNDED_WILDCARD -> resultSubstitutor.substitute(PsiWildcardType.createUnbounded(metaData.virtualMethod.manager))
    }
    if (hint != InstantiationHint.ENDPOINT_TYPE_PARAMETER) {
      resultSubstitutor = resultSubstitutor.put(unit.core.initialTypeParameter, transformedType)
    }
  }
  val endpointTypes = endpoints.map {
    val completelySubstitutedType = resultSubstitutor.recursiveSubstitute(it.typeInstantiation)
    collector.createBoundedTypeParameter(it.type.name, resultSubstitutor, completelySubstitutedType).type()
  }
  val endpointSubstitutor = PsiSubstitutor.EMPTY.putAll(endpoints.map { it.core.initialTypeParameter }.toTypedArray(),
                                                        endpointTypes.toTypedArray())
  acceptFinalSubstitutor(metaData, resultSubstitutor.putAll(endpointSubstitutor), collector, constantParameters)
}
