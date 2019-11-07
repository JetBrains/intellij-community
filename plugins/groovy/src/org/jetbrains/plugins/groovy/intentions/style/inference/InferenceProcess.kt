// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.search.SearchScope
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

/**
 * Performs full substitution for non-typed parameters of [method]
 * Inference is performed in 3 phases:
 * 1. Creating a type parameter for each of existing non-typed parameters
 * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
 * 3. Inferring dependencies between new type parameters and instantiating them.
 */
fun runInferenceProcess(method: GrMethod, scope: SearchScope): GrMethod {
  val originalMethod = getOriginalMethod(method)
  val overridableMethod = findOverridableMethod(originalMethod)
  if (overridableMethod != null) {
    return convertToGroovyMethod(overridableMethod)
  }
  val driver = createDriver(originalMethod, scope)
  val signatureSubstitutor = driver.collectSignatureSubstitutor().removeForeignTypeParameters(method)
  val virtualMethod = createVirtualMethod(method) ?: return method
  val parameterizedDriver = driver.createParameterizedDriver(ParameterizationManager(method), virtualMethod, signatureSubstitutor)
  val typeUsage = parameterizedDriver.collectInnerConstraints()
  val graph = setUpGraph(virtualMethod, method.typeParameters.asList(), typeUsage)
  val inferredGraph = determineDependencies(graph)
  return instantiateTypeParameters(parameterizedDriver, inferredGraph, method, typeUsage)
}

private fun createDriver(method: GrMethod,
                         scope: SearchScope): InferenceDriver {
  val virtualMethod = createVirtualMethod(method) ?: return EmptyDriver
  val generator = NameGenerator("_START" + method.hashCode(), context = method)
  return CommonDriver.createFromMethod(method, virtualMethod, generator, scope)
}

private fun setUpGraph(virtualMethod: GrMethod,
                       constantTypes: List<PsiTypeParameter>,
                       typeUsage: TypeUsageInformation): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(virtualMethod.typeParameters, context = virtualMethod)
  typeUsage.fillSession(inferenceSession)
  inferenceSession.infer()
  return createGraphFromInferenceVariables(inferenceSession, virtualMethod, typeUsage, constantTypes)
}