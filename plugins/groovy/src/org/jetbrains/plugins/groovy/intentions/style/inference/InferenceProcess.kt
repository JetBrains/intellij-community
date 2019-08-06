// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.PsiIntersectionType.createIntersection
import com.intellij.psi.PsiIntersectionType.flatten
import com.intellij.psi.search.SearchScope
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
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
fun runInferenceProcess(method: GrMethod, scope: SearchScope): GrMethod {
  val overridableMethod = findOverridableMethod(method)
  if (overridableMethod != null) {
    return convertToGroovyMethod(overridableMethod)
  }
  val driver = createDriver(method, scope)
  val signatureSubstitutor = driver.collectSignatureSubstitutor().removeForeignTypeParameters(method)
  val virtualMethod = createVirtualMethod(method)
  val parameterizedDriver = driver.createParameterizedDriver(ParameterizationManager(method), virtualMethod, signatureSubstitutor)
  val typeUsage = parameterizedDriver.collectInnerConstraints()
  val graph = setUpGraph(parameterizedDriver, virtualMethod, method.typeParameters.asList(), typeUsage)
  return inferTypeParameters(parameterizedDriver, graph, method, typeUsage)
}

private fun createDriver(method: GrMethod,
                         scope: SearchScope): InferenceDriver {
  val virtualMethod = createVirtualMethod(method)
  val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
  return CommonDriver.createFromMethod(method, virtualMethod, generator, scope)
}

private fun setUpGraph(driver: InferenceDriver,
                       virtualMethod: GrMethod,
                       constantTypes: List<PsiTypeParameter>,
                       typeUsage: TypeUsageInformation): InferenceUnitGraph {
  val inferenceSession = CollectingGroovyInferenceSession(virtualMethod.typeParameters, context = virtualMethod)
  typeUsage.constraints.forEach { inferenceSession.addConstraint(it) }
  inferenceSession.infer()
  val forbiddingTypes = typeUsage.contravariantTypes.filter { it in typeUsage.dependentTypes.map { deptype -> deptype.type() } } +
                        virtualMethod.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType } +
                        driver.forbiddingTypes()
  return createGraphFromInferenceVariables(inferenceSession, virtualMethod, forbiddingTypes, typeUsage, constantTypes)
}

private fun inferTypeParameters(driver: InferenceDriver,
                                initialGraph: InferenceUnitGraph,
                                method: GrMethod,
                                usage: TypeUsageInformation): GrMethod {
  val inferredGraph = determineDependencies(initialGraph)
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  val collector = TypeParameterCollector(method)
  val equivalenceClasses = inferredGraph.units.groupBy {
    if (it.direct && it.typeInstantiation.resolve() is PsiTypeParameter) {
      it.typeInstantiation
    }
    else {
      it.type
    }
  }
  for (unit in inferredGraph.resolveOrder()) {
    val (instantiation, hint) = unit.smartTypeInstantiation(usage, equivalenceClasses)
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
      InstantiationHint.EXTENDS_WILDCARD -> {
        if (instantiation == PsiType.NULL || instantiation == getJavaLangObject(method)) {
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
  val finalSubstitutor = resultSubstitutor.putAll(endpointSubstitutor)
  val resultMethod = createVirtualMethod(method)
  driver.instantiate(resultMethod, finalSubstitutor)
  resultMethod.typeParameterList!!.replace(collector.typeParameterList)
  val residualTypeParameterList = buildResidualTypeParameterList(resultMethod, driver, method)
  if (resultMethod.typeParameters.isEmpty()) {
    resultMethod.typeParameterList?.delete()
  }
  else {
    resultMethod.typeParameterList!!.replace(residualTypeParameterList)
  }
  return resultMethod
}


private fun buildResidualTypeParameterList(resultMethod: GrMethod,
                                           driver: InferenceDriver,
                                           method: GrMethod): PsiTypeParameterList {
  val factory = GroovyPsiElementFactory.getInstance(resultMethod.project)
  val necessaryTypeParameters = mutableSetOf<PsiTypeParameter>()
  val necessaryTypeNames = mutableSetOf<String>()
  val classParameters = (resultMethod.containingClass?.typeParameters?.map { it.name!! } ?: emptyList()).toSet()
  val visitor = object : PsiTypeVisitor<Unit>() {

    override fun visitClassType(classType: PsiClassType?) {
      classType ?: return
      val resolvedClass = classType.typeParameter()
      if (resolvedClass != null && resolvedClass.name !in classParameters) {
        if (resolvedClass.name !in necessaryTypeNames) {
          necessaryTypeParameters.add(resolvedClass)
          necessaryTypeNames.add(resolvedClass.name!!)
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
  val takenNames = necessaryTypeParameters.mapNotNull { it.name }
  val remainedConstantParameters = method.typeParameters.filter { it.name !in takenNames || it.name in restrictedNames }
  return factory.createMethodFromText(
    "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString { it.text }}> void foo() {}")
    .typeParameterList!!
}

private fun collectClassParameters(clazz: PsiClass?): MutableList<PsiTypeParameter> {
  clazz ?: return mutableListOf()
  return collectClassParameters(clazz.containingClass).apply { addAll(clazz.typeParameters) }
}

class TypeParameterCollector(context: PsiElement) {
  val factory = GroovyPsiElementFactory.getInstance(context.project)
  val typeParameterList: PsiTypeParameterList = factory.createTypeParameterList()

  fun createBoundedTypeParameter(name: String,
                                 resultSubstitutor: PsiSubstitutor,
                                 advice: PsiType): PsiTypeParameter {
    val mappedSupertypes = when (advice) {
      is PsiClassType -> resultSubstitutor.substitute(advice)
      is PsiIntersectionType -> createIntersection(flatten(advice.conjuncts, mutableSetOf()).map {
        resultSubstitutor.substitute(it)
      })
      else -> null
    }
    return factory.createProperTypeParameter(name, mappedSupertypes).apply {
      this@TypeParameterCollector.typeParameterList.add(this)
    }
  }
}