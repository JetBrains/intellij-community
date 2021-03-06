// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

/**
 * Performs full substitution for non-typed parameters of [method]
 * Inference is performed in 3 phases:
 * 1. Creating a type parameter for each of existing non-typed parameters
 * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
 * 3. Inferring dependencies between new type parameters and instantiating them.
 */
fun runInferenceProcess(method: GrMethod, options: SignatureInferenceOptions): GrMethod {
  val originalMethod = getOriginalMethod(method)
  val overridableMethod = findOverridableMethod(originalMethod)
  if (overridableMethod != null) {
    return convertToGroovyMethod(overridableMethod) ?: method
  }
  val searchScope = getSearchScope(method, options.shouldUseReducedScope) ?: return method
  val environment = SignatureInferenceEnvironment(originalMethod, searchScope, options.signatureInferenceContext)
  val driver = createDriver(originalMethod, environment)
  val signatureSubstitutor = driver.collectSignatureSubstitutor().removeForeignTypeParameters(method)
  val virtualMethodPointer: SmartPsiElementPointer<GrMethod> = createVirtualMethod(method) ?: return method
  val parameterizedDriver = run {
    val virtualMethod = virtualMethodPointer.element ?: return method
    driver.createParameterizedDriver(ParameterizationManager(method), virtualMethod, signatureSubstitutor)
  }
  val typeUsage = parameterizedDriver.collectInnerConstraints()
  val graph = run {
    val virtualMethod = virtualMethodPointer.element ?: return method
    setUpGraph(virtualMethod, method.typeParameters.asList(), typeUsage)
  }
  val inferredGraph = determineDependencies(graph)
  return instantiateTypeParameters(parameterizedDriver, inferredGraph, method, typeUsage)
}

private val forbiddenAnnotations =
  setOf(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO,
        GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS)

internal fun GrParameter.eligibleForExtendedInference(): Boolean =
  typeElement == null //|| (type.isClosureType() && (annotations.map { it.qualifiedName } intersect forbiddenAnnotations).isEmpty())

private fun createDriver(method: GrMethod, environment: SignatureInferenceEnvironment): InferenceDriver {
  val virtualMethodPointer: SmartPsiElementPointer<GrMethod> = createVirtualMethod(method) ?: return EmptyDriver
  val generator = NameGenerator("_START" + method.hashCode(), context = method)
  return CommonDriver.createFromMethod(method, virtualMethodPointer, generator, environment)
}

private fun setUpGraph(virtualMethod: GrMethod,
                       constantTypes: List<PsiTypeParameter>,
                       typeUsage: TypeUsageInformation): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(virtualMethod.typeParameters, context = virtualMethod)
  typeUsage.fillSession(inferenceSession)
  inferenceSession.infer()
  return createGraphFromInferenceVariables(inferenceSession, virtualMethod, typeUsage, constantTypes)
}