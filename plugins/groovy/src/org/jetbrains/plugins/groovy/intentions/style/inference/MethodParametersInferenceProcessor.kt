// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.InferenceDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.setUpParameterMapping
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode.Companion.InstantiationHint
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * Performs full substitution for non-typed parameters of [method]
 * Inference is performed in 3 phases:
 * 1. Creating a type parameter for each of existing non-typed parameters
 * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
 * 3. Inferring dependencies between new type parameters and instantiating them.
 */
fun runInferenceProcess(method: GrMethod): GrMethod {
  val overridableMethod = findOverridableMethod(method)
  if (overridableMethod != null) {
    return convertToGroovyMethod(overridableMethod)
  }
  val driver = InferenceDriver.createDriverFromMethod(method)
  val signatureSubstitutor = setUpParametersSignature(driver)
  if (method.isConstructor) {
    return driver.instantiate(signatureSubstitutor)
  }
  else {
    val parameterizedDriver = InferenceDriver.createParameterizedDriver(driver, signatureSubstitutor)
    val graph = setUpGraph(parameterizedDriver)
    return inferTypeParameters(parameterizedDriver, graph)
  }
}

private fun setUpParametersSignature(driver: InferenceDriver): PsiSubstitutor {
  val mapping = setUpParameterMapping(driver.method, driver.virtualMethod).map { it.key.name to it.value}.toMap()
  val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod,
                                                          mapping)
  driver.collectOuterConstraints().forEach { inferenceSession.addConstraint(it) }
  return inferenceSession.inferSubst()
}


private fun setUpGraph(driver: InferenceDriver): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(driver.virtualMethod.typeParameters, PsiSubstitutor.EMPTY, driver.virtualMethod)
  val typeUsage = driver.collectInnerConstraints()
  typeUsage.constraints.forEach { inferenceSession.addConstraint(it) }
  inferenceSession.infer()
  return createGraphFromInferenceVariables(inferenceSession, driver, typeUsage)
}

private fun inferTypeParameters(driver: InferenceDriver,
                                initialGraph: InferenceUnitGraph): GrMethod {
  val inferredGraph = determineDependencies(initialGraph)
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  val collector = TypeParameterCollector(driver.virtualMethod)
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
      InstantiationHint.WILDCARD -> {
        if (instantiation == PsiType.NULL) {
          resultSubstitutor.substitute(PsiWildcardType.createUnbounded(driver.virtualMethod.manager))
        }
        else {
          resultSubstitutor.substitute(PsiWildcardType.createExtends(driver.virtualMethod.manager, instantiation))
        }
      }
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
  return driver.instantiate(resultSubstitutor.putAll(endpointSubstitutor), collector.typeParameterList)
}


class TypeParameterCollector(context: PsiElement) {
  val project = context.project
  val typeParameterList: MutableList<PsiTypeParameter> = mutableListOf()

  fun createBoundedTypeParameter(name: String,
                                 resultSubstitutor: PsiSubstitutor,
                                 advice: PsiType): PsiTypeParameter {
    val mappedSupertypes = when (advice) {
      is PsiClassType -> arrayOf(resultSubstitutor.substitute(advice) as PsiClassType)
      is PsiIntersectionType -> PsiIntersectionType.flatten(advice.conjuncts, mutableSetOf()).map {
        resultSubstitutor.substitute(it) as PsiClassType
      }.toTypedArray()
      else -> emptyArray()
    }
    return GroovyPsiElementFactory.getInstance(project).createProperTypeParameter(name, mappedSupertypes).apply {
      this@TypeParameterCollector.typeParameterList.add(this)
    }
  }
}