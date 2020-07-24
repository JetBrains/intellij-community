// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.createGraphFromInferenceVariables
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.determineDependencies
import org.jetbrains.plugins.groovy.intentions.style.inference.search.searchWithClosureAvoidance
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import kotlin.LazyThreadSafetyMode.NONE

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
  val newOptions = options.copy(calls = lazy(NONE) {
    val restrictedScope = if (options.restrictScopeToLocal) {
      LocalSearchScope(arrayOf(originalMethod.containingFile), null, true)
    }
    else options.searchScope
    searchWithClosureAvoidance(originalMethod, restrictedScope).sortedBy { it.element.textOffset }
  })
  val driver = createDriver(originalMethod, newOptions)
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

private fun createDriver(method: GrMethod, options: SignatureInferenceOptions): InferenceDriver {
  val virtualMethodPointer: SmartPsiElementPointer<GrMethod> = createVirtualMethod(method) ?: return EmptyDriver
  val generator = NameGenerator("_START" + method.hashCode(), context = method)
  return CommonDriver.createFromMethod(method, virtualMethodPointer, generator, options)
}

private fun setUpGraph(virtualMethod: GrMethod,
                       constantTypes: List<PsiTypeParameter>,
                       typeUsage: TypeUsageInformation): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(virtualMethod.typeParameters, context = virtualMethod)
  typeUsage.fillSession(inferenceSession)
  inferenceSession.infer()
  return createGraphFromInferenceVariables(inferenceSession, virtualMethod, typeUsage, constantTypes)
}