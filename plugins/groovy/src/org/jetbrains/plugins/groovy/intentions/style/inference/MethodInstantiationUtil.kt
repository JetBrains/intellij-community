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


fun instantiateTypeParameters(driver: InferenceDriver,
                              inferredGraph: InferenceUnitGraph,
                              method: GrMethod,
                              usage: TypeUsageInformation): GrMethod {
  val (completeSubstitutor, completeTypedMethod) = createCompleteSubstitutor(method, inferredGraph, usage)
  val reducingDriver = driver.acceptTypeVisitor(SubstitutorTypeMapper(completeSubstitutor), completeTypedMethod)
  val (reducedMethod, remainedNames) = buildResidualTypeParameterList(completeTypedMethod, reducingDriver, method)
  val (renamedList, nameMap) = renameTypeParameters(reducedMethod, method, remainedNames)
  val renamingDriver = reducingDriver.acceptTypeVisitor(RenamingTypeMapper(nameMap, reducedMethod), reducedMethod)
  renamingDriver.instantiate(reducedMethod)
  for (typeParameter in reducedMethod.typeParameters) {
    if (typeParameter.name !in nameMap.keys && typeParameter.name !in nameMap.values) {
      renamedList.add(typeParameter)
    }
  }
  if (reducedMethod.typeParameters.isEmpty()) {
    reducedMethod.typeParameterList?.delete()
  }
  else {
    reducedMethod.typeParameterList!!.replace(renamedList)
  }
  return reducedMethod
}

fun createVirtualMethodWithoutVararg(method: GrMethod, typeParameterList: PsiTypeParameterList) : GrMethod {
  val virtualMethod = createVirtualMethod(method, typeParameterList) ?: return method
  virtualMethod.parameters.lastOrNull()?.ellipsisDots?.delete()
  return virtualMethod
}


class SubstitutorTypeMapper(private val substitutor: PsiSubstitutor) : PsiTypeMapper() {
  private val typeParameters = substitutor.substitutionMap.keys

  override fun visitClassType(classType: PsiClassType?): PsiType? {
    val correctClassType = typeParameters.find { it.name == classType?.name }?.type() ?: classType
    return substitutor.substitute(correctClassType)
  }

  override fun visitArrayType(type: PsiArrayType?): PsiType? {
    type ?: return type
    val substitution = removeWildcard(type.componentType.accept(this))
    return substitution.createArrayType()

  }

}


class RenamingTypeMapper(private val nameMap: Map<in String, String>,
                         private val sourceMethod: GrMethod?) : PsiTypeMapper() {
  private val sourceTypeParameterNames = sourceMethod?.typeParameters?.map { it.name }

  override fun visitClassType(classType: PsiClassType): PsiType {
    val resolved = classType.resolve() ?: return classType
    if (resolved is PsiTypeParameter && classType.name in nameMap) {
      val newName = nameMap.getValue(classType.name)
      return if (sourceMethod != null && newName in sourceTypeParameterNames!!) {
        sourceMethod.typeParameters.find { it.name == newName }!!.type()
      }
      else {
        PsiClassType.getTypeByName(newName, resolved.project, resolved.resolveScope)
      }
    }
    else return GroovyPsiElementFactory.getInstance(resolved.project)
      .createType(resolved, *classType.parameters.map { it.accept(this) }.toTypedArray())
  }

}

fun renameTypeParameters(resultMethod: GrMethod,
                         method: GrMethod,
                         remainedNames: Set<String>): Pair<PsiTypeParameterList, Map<String, String>> {
  val nameGenerator = NameGenerator(context = method)
  val collector = TypeParameterCollector(method)
  val nameMap = remainedNames.map { it to nameGenerator.name }.toMap()
  val renamingMapper = RenamingTypeMapper(nameMap, null)
  for (typeParameter in resultMethod.typeParameters.filter { it.name in remainedNames }) {
    val newType = typeParameter.upperBound().accept(renamingMapper)
    collector.createBoundedTypeParameter(nameMap[typeParameter.name]!!, PsiSubstitutor.EMPTY, newType)
  }
  val validPsiTypeParameterList = collector.validPsiTypeParameterList()
  val existingNames = resultMethod.typeParameters.map { it.name }
  for (newTypeParameter in validPsiTypeParameterList.typeParameters) {
    if (newTypeParameter.name !in existingNames) {
      resultMethod.typeParameterList!!.add(newTypeParameter.copy())
    }
  }
  return validPsiTypeParameterList to nameMap
}

fun createCompleteSubstitutor(method: GrMethod,
                              inferredGraph: InferenceUnitGraph,
                              usage: TypeUsageInformation): Pair<PsiSubstitutor, GrMethod> {
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
  val completeTypedMethod = createVirtualMethodWithoutVararg(method, collector.typeParameterList)
  return resultSubstitutor.putAll(endpointSubstitutor) to completeTypedMethod
}

private fun buildResidualTypeParameterList(resultMethod: GrMethod,
                                           driver: InferenceDriver,
                                           method: GrMethod): Pair<GrMethod, Set<String>> {
  val factory = GroovyPsiElementFactory.getInstance(resultMethod.project)
  val necessaryTypeParameters = mutableSetOf<PsiTypeParameter>()
  val necessaryTypeNames = mutableSetOf<String>()
  val outerClassParameters = collectClassParameters(resultMethod.containingClass).map { it.name!! }.toSet()
  val visitor = object : PsiTypeMapper() {

    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
      val resolvedTypeParameter = classType.typeParameter()
      if (resolvedTypeParameter != null &&
          resolvedTypeParameter.name.run { this !in outerClassParameters && this !in necessaryTypeNames }) {
        necessaryTypeParameters.add(resolvedTypeParameter)
        necessaryTypeNames.add(resolvedTypeParameter.name!!)
        resolvedTypeParameter.extendsList.referencedTypes.forEach { it.accept(this) }
      }
      classType.parameters.forEach { it.accept(this) }
      return classType
    }

  }
  driver.acceptTypeVisitor(visitor, resultMethod)
  val takenNames = necessaryTypeParameters.mapNotNull { it.name }
  val remainedConstantParameters = method.typeParameters.filter { it.name !in takenNames || it.name in outerClassParameters }
  val resultTypeParameterList = factory
    .createMethodFromText("def <${(remainedConstantParameters + necessaryTypeParameters).joinToString { it.text }}> void foo() {}")
    .typeParameterList!!
  val newMethod = createVirtualMethodWithoutVararg(method, resultTypeParameterList)
  return newMethod to (necessaryTypeNames - method.typeParameters.map { it.name!! })
}

private fun collectClassParameters(clazz: PsiClass?): MutableList<PsiTypeParameter> {
  clazz ?: return mutableListOf()
  return collectClassParameters(clazz.containingClass).apply { addAll(clazz.typeParameters) }
}

class TypeParameterCollector(context: PsiElement) {
  val factory = GroovyPsiElementFactory.getInstance(context.project)
  val typeParameterList: PsiTypeParameterList = (factory).createTypeParameterList()

  // this method handles annoying bug with non-fading PSI error registered on empty type parameter list
  fun validPsiTypeParameterList(): PsiTypeParameterList {
    return factory.createMethodFromText("def ${typeParameterList.text} void foo() {}").typeParameterList!!
  }

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

