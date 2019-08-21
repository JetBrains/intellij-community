// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.InferenceDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.getJavaLangObject
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode.Companion.InstantiationHint.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


internal fun inferTypeParameters(driver: InferenceDriver,
                                 inferredGraph: InferenceUnitGraph,
                                 method: GrMethod,
                                 usage: TypeUsageInformation): GrMethod {
  val (finalSubstitutor, typeParameterList) = createCompleteSubstitutor(method, inferredGraph, usage)
  val resultMethod = createVirtualMethod(method) ?: return method
  driver.instantiate(resultMethod, finalSubstitutor)
  resultMethod.typeParameterList?.replace(typeParameterList)
  val residualTypeParameterList = buildResidualTypeParameterList(resultMethod, driver, method)
  if (resultMethod.typeParameters.isEmpty()) {
    resultMethod.typeParameterList?.delete()
  }
  else {
    resultMethod.typeParameterList!!.replace(residualTypeParameterList)
  }
  return resultMethod
}

fun createCompleteSubstitutor(method: GrMethod,
                              inferredGraph: InferenceUnitGraph,
                              usage: TypeUsageInformation): Pair<PsiSubstitutor, PsiTypeParameterList> {
  var resultSubstitutor = PsiSubstitutor.EMPTY
  val endpoints = mutableSetOf<InferenceUnitNode>()
  val collector = TypeParameterCollector(method)
  val equivalenceClasses = inferredGraph.units.groupBy {
    if (it.direct) it.typeInstantiation else it.type
  }
  for (unit in inferredGraph.resolveOrder()) {
    val (instantiation, hint) = unit.smartTypeInstantiation(usage, equivalenceClasses)
    val transformedType = when (hint) {
      NEW_TYPE_PARAMETER ->
        collector.createBoundedTypeParameter(unit.type.name, resultSubstitutor, resultSubstitutor.substitute(instantiation)).type()
      REIFIED_AS_PROPER_TYPE ->
        resultSubstitutor.substitute(instantiation)
      ENDPOINT_TYPE_PARAMETER ->
        instantiation.apply { endpoints.add(unit) }
      REIFIED_AS_TYPE_PARAMETER ->
        instantiation.apply { collector.typeParameterList.add(unit.core.initialTypeParameter) }
      EXTENDS_WILDCARD ->
        if (instantiation == PsiType.NULL || instantiation == getJavaLangObject(method)) {
          PsiWildcardType.createUnbounded(method.manager)
        }
        else {
          resultSubstitutor.substitute(PsiWildcardType.createExtends(method.manager, instantiation))
        }
    }
    if (hint != ENDPOINT_TYPE_PARAMETER) {
      resultSubstitutor = resultSubstitutor.put(unit.core.initialTypeParameter, transformedType)
    }
  }
  val endpointTypes = endpoints.map {
    val completelySubstitutedEndpointType = resultSubstitutor.recursiveSubstitute(it.typeInstantiation)
    collector.createBoundedTypeParameter(it.type.name, resultSubstitutor, completelySubstitutedEndpointType).type()
  }
  val endpointTypeParameters = endpoints.map { it.core.initialTypeParameter }
  val endpointSubstitutor = PsiSubstitutor.EMPTY.putAll(endpointTypeParameters.toTypedArray(), endpointTypes.toTypedArray())
  return resultSubstitutor.putAll(endpointSubstitutor) to collector.typeParameterList
}

private fun buildResidualTypeParameterList(resultMethod: GrMethod,
                                           driver: InferenceDriver,
                                           method: GrMethod): PsiTypeParameterList {
  val factory = GroovyPsiElementFactory.getInstance(resultMethod.project)
  val necessaryTypeParameters = mutableSetOf<PsiTypeParameter>()
  val necessaryTypeNames = mutableSetOf<String>()
  val outerClassParameters = collectClassParameters(resultMethod.containingClass).map { it.name!! }.toSet()
  val visitor = object : PsiTypeVisitor<Unit>() {

    override fun visitClassType(classType: PsiClassType?) {
      classType ?: return
      val resolvedTypeParameter = classType.typeParameter()
      if (resolvedTypeParameter != null &&
          resolvedTypeParameter.name.run { this !in outerClassParameters && this !in necessaryTypeNames }) {
        necessaryTypeParameters.add(resolvedTypeParameter)
        necessaryTypeNames.add(resolvedTypeParameter.name!!)
        resolvedTypeParameter.extendsList.referencedTypes.forEach { it.accept(this) }
      }
      classType.parameters.forEach { it.accept(this) }
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?) = wildcardType?.extendsBound?.accept(this)

    override fun visitArrayType(arrayType: PsiArrayType?) = arrayType?.componentType?.accept(this)
  }
  driver.acceptReducingVisitor(visitor, resultMethod)
  val takenNames = necessaryTypeParameters.mapNotNull { it.name }
  val remainedConstantParameters = method.typeParameters.filter { it.name !in takenNames || it.name in outerClassParameters }
  return factory
    .createMethodFromText("def <${(remainedConstantParameters + necessaryTypeParameters).joinToString { it.text }}> void foo() {}")
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
    val mappedSupertypes = when (val newAdvice = removeWildcard(advice)) {
      is PsiClassType -> resultSubstitutor.substitute(newAdvice)
      is PsiIntersectionType -> PsiIntersectionType.createIntersection(
        PsiIntersectionType.flatten(newAdvice.conjuncts, mutableSetOf()).map { resultSubstitutor.substitute(it) })
      else -> null
    }
    return factory.createProperTypeParameter(name, mappedSupertypes).apply {
      this@TypeParameterCollector.typeParameterList.add(this)
    }
  }
}


internal fun removeWildcard(advice: PsiType): PsiType =
  when (advice) {
    is PsiWildcardType -> advice.extendsBound
    else -> advice
  }

