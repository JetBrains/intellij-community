// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.CommonDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.InferenceDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.ParameterizationManager
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
  val driver = createDriver(method)
  val signatureSubstitutor = setUpParametersSignature(driver)
  val (parameterizedDriver, virtualMethod) = createParameterizedDriver(driver, method, signatureSubstitutor)
  val graph = setUpGraph(parameterizedDriver, virtualMethod, method.typeParameters.asList())
  return inferTypeParameters(parameterizedDriver, graph, method)
}

private fun createDriver(method: GrMethod): InferenceDriver {
  val virtualMethod = createVirtualMethod(method)
  val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
  return CommonDriver.createFromMethod(method, virtualMethod, generator)
}

private fun setUpParametersSignature(driver: InferenceDriver): PsiSubstitutor {
  return driver.collectSignatureSubstitutor()
}

private fun createParameterizedDriver(driver: InferenceDriver,
                                      method: GrMethod,
                                      substitutor: PsiSubstitutor): Pair<InferenceDriver, GrMethod> {
  val parameterizationManager = ParameterizationManager(method)
  val parameterizedMethod = createVirtualMethod(method)
  parameterizedMethod.parameters.lastOrNull()?.ellipsisDots?.delete()
  return driver.createParameterizedDriver(parameterizationManager, parameterizedMethod, substitutor) to parameterizedMethod
}


private fun setUpGraph(driver: InferenceDriver, virtualMethod: GrMethod, constantTypes: List<PsiTypeParameter>): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(virtualMethod.typeParameters, context = virtualMethod)
  val typeUsage = driver.collectInnerConstraints()
  typeUsage.constraints.forEach { inferenceSession.addConstraint(it) }
  inferenceSession.infer()
  return createGraphFromInferenceVariables(inferenceSession, virtualMethod, driver, typeUsage, constantTypes)
}

private fun inferTypeParameters(driver: InferenceDriver,
                                initialGraph: InferenceUnitGraph, method: GrMethod): GrMethod {
  val inferredGraph = determineDependencies(initialGraph)
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  val collector = TypeParameterCollector(method)
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
          resultSubstitutor.substitute(PsiWildcardType.createUnbounded(method.manager))
        }
        else {
          resultSubstitutor.substitute(PsiWildcardType.createExtends(method.manager, instantiation))
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
  val resultMethod = createVirtualMethod(method)
  if (resultMethod.parameters.last().typeElementGroovy == null) {
    resultMethod.parameters.last().ellipsisDots?.delete()
  }
  val finalSubstitutor = resultSubstitutor.putAll(endpointSubstitutor)
  driver.instantiate(resultMethod, finalSubstitutor)
  val residualTypeParameterList = buildResidualTypeParameterList(collector.typeParameterList, resultMethod, driver, method)
  resultMethod.typeParameterList!!.replace(residualTypeParameterList)
  if (resultMethod.typeParameters.isEmpty()) {
    resultMethod.typeParameterList?.delete()
  }
  return resultMethod
}


private fun buildResidualTypeParameterList(typeParameters: Collection<PsiTypeParameter>,
                                           resultMethod: GrMethod,
                                           driver: InferenceDriver,
                                           method: GrMethod): PsiTypeParameterList {
  val factory = GroovyPsiElementFactory.getInstance(resultMethod.project)
  val list = factory.createTypeParameterList()
  typeParameters.forEach { list.add(it) }
  resultMethod.typeParameterList!!.replace(list)
  val necessaryTypeParameters = mutableSetOf<PsiTypeParameter>()
  val visitor = object : PsiTypeVisitor<Unit>() {

    override fun visitClassType(classType: PsiClassType?) {
      classType ?: return
      val resolvedClass = classType.resolveGenerics().element
      if (resolvedClass is PsiTypeParameter) {
        if (resolvedClass.name !in necessaryTypeParameters.map { it.name }) {
          necessaryTypeParameters.add(resolvedClass)
          resolvedClass.extendsList.referencedTypes.forEach { it.accept(this) }
        }
      }
      classType.parameters.forEach { it.accept(this) }
      super.visitClassType(classType)
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?) {
      wildcardType?.extendsBound?.accept(this)
      super.visitWildcardType(wildcardType)
    }

    override fun visitArrayType(arrayType: PsiArrayType?) {
      arrayType?.componentType?.accept(this)
      super.visitArrayType(arrayType)
    }
  }
  driver.acceptReducingVisitor(visitor, resultMethod)
  val restrictedNames = collectClassParameters(method.containingClass).map { it.name }
  necessaryTypeParameters.removeIf { it.name in restrictedNames }
  val takenNames = necessaryTypeParameters.map { it.name }
  val remainedConstantParameters = method.typeParameters.filter { it.name !in takenNames }
  return factory.createMethodFromText(
    "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString(", ") { it.text }}> void foo() {}")
    .typeParameterList!!
}

private fun collectClassParameters(clazz: PsiClass?): MutableList<PsiTypeParameter> {
  clazz ?: return mutableListOf()
  return collectClassParameters(clazz.containingClass).apply { addAll(clazz.typeParameters) }
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