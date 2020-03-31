// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.*
import com.intellij.psi.PsiIntersectionType.createIntersection
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.getJavaLangObject
import org.jetbrains.plugins.groovy.intentions.style.inference.flattenIntersections
import org.jetbrains.plugins.groovy.intentions.style.inference.getInferenceVariable
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.rawType
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


fun createGraphFromInferenceVariables(session: GroovyInferenceSession,
                                      virtualMethod: GrMethod,
                                      usageInformation: TypeUsageInformation,
                                      constantTypes: List<PsiTypeParameter>): InferenceUnitGraph {
  val variableMap = BidirectionalMap<InferenceUnit, InferenceVariable>()
  val builder = InferenceUnitGraphBuilder()
  val constantNames = constantTypes.mapNotNull { it.name }.toMutableList()
  val variables = virtualMethod.typeParameters.mapNotNull { getInferenceVariable(session, it.type()) }
  for (variable in variables) {
    val variableType = variable.parameter.type()
    val (instantiation, isStrict) = inferType(variable.parameter, usageInformation)
    val core = InferenceUnit(variable.parameter,
                             constant = variableType.name in constantNames)
    if (variableType.name !in constantNames) {
      builder.setType(core, instantiation)
    }
    else {
      builder.setType(core, variable.parameter.extendsListTypes.firstOrNull() ?: PsiType.NULL)
    }
    if (isStrict) {
      builder.setDirect(core)
    }
    variableMap[core] = variable
  }
  for ((unit, variable) in variableMap.entries.sortedBy { it.key.toString() }) {
    val upperBounds = variable.getBounds(InferenceBound.UPPER) + variable.getBounds(InferenceBound.EQ)
    deepConnect(session, variableMap, upperBounds) { builder.addRelation(it, unit) }
    val lowerBounds = variable.getBounds(InferenceBound.LOWER) + variable.getBounds(InferenceBound.EQ)
    deepConnect(session, variableMap, lowerBounds) { builder.addRelation(unit, it) }
  }
  return builder.build()
}

private fun deepConnect(session: GroovyInferenceSession,
                        map: BidirectionalMap<InferenceUnit, InferenceVariable>,
                        bounds: List<PsiType>,
                        relationHandler: (InferenceUnit) -> Unit) {
  for (dependency in bounds.flattenIntersections().mapNotNull { session.getInferenceVariable(it) }) {
    if (dependency in map.values) {
      relationHandler(map.getKeysByValue(dependency)!!.first())
    }
  }
}

private fun findStrictClass(constraints: Collection<BoundConstraint>): PsiType? {
  val strictTypes = constraints.mapNotNull {
    when (it.marker) {
      EQUAL -> it.type
      else -> null
    }
  }
  return strictTypes.maxWith(Comparator { left, right ->
    if (TypesUtil.canAssign(left, right, left.resolve()!!.context!!, METHOD_PARAMETER) == OK) 1 else -1
  })
}


fun inferType(parameter: PsiTypeParameter, usage: TypeUsageInformation): Pair<PsiType, Boolean> {
  val constraints = usage.requiredClassTypes[parameter]?.groupBy { it.marker } ?: emptyMap()
  val signatureTypes = parameter.extendsList.referencedTypes.toList()
  val strictClass = findStrictClass(constraints[EQUAL] ?: emptyList())
  if (strictClass != null) {
    return (signatureTypes.find { it.resolve() == strictClass } ?: strictClass) to true
  }
  else {
    return completeInstantiation(parameter, usage, constraints, signatureTypes) to false
  }

}

private fun completeInstantiation(parameter: PsiTypeParameter,
                                  usageInformation: TypeUsageInformation,
                                  constraints: Map<ContainMarker, List<BoundConstraint>>,
                                  signatureTypes: List<PsiClassType>): PsiType {
  val context = parameter.context!!
  val typeLattice = TypeLattice(context)
  val javaLangObject = getJavaLangObject(parameter)
  val parameterType = parameter.type()
  val superClasses = constraints[UPPER]?.map { it.type } ?: emptyList()
  val subClasses = constraints[LOWER]?.map { it.type } ?: emptyList()
  val inhabitingClasses = constraints[INHABIT]?.map { it.type } ?: emptyList()
  return when (parameterType) {

    in usageInformation.contravariantTypes -> {
      val lowerBound = when {
        inhabitingClasses.isNotEmpty() && subClasses.all { !it.isTypeParameter() } -> typeLattice.meet(inhabitingClasses)
        else -> typeLattice.join(subClasses)
      }.mapConjuncts { signatureTypes.findTypeWithCorrespondingSupertype(it) }
      when {
        lowerBound != PsiType.NULL -> PsiWildcardType.createSuper(context.manager, lowerBound)
        else -> PsiWildcardType.createUnbounded(context.manager)
      }
    }

    in usageInformation.covariantTypes -> {
      val upperBound = when {
        superClasses.any { it != javaLangObject } -> typeLattice.meet(superClasses)
        else -> typeLattice.join(inhabitingClasses)
      }.mapConjuncts { signatureTypes.findTypeWithCorrespondingSupertype(it) }
      when {
        upperBound.resolve()?.modifierList?.hasModifierProperty("final") ?: false -> upperBound
        (upperBound != javaLangObject) -> PsiWildcardType.createExtends(context.manager, upperBound)
        else -> PsiWildcardType.createUnbounded(context.manager)
      }
    }

    else -> {
      val upperBound = typeLattice.meet(superClasses)
      val lowerBound = typeLattice.join(subClasses + inhabitingClasses).mapConjuncts { if (it.isGroovyLangObject()) javaLangObject else it }
      val adjustedBound = lowerBound.findSingleClass()?.takeIf { upperBound == javaLangObject } ?: upperBound
      val invariantUpperBound = adjustedBound.mapConjuncts { signatureTypes.findTypeWithCorrespondingSupertype(it) }
      invariantUpperBound
    }
  }
}

private fun PsiType.findSingleClass(): PsiType? {
  return when (this) {
    is PsiIntersectionType -> conjuncts.find { it.resolve()?.isInterface?.not() ?: false }
    else -> this
  }
}

fun List<PsiClassType>.findTypeWithCorrespondingSupertype(pattern: PsiType): PsiType {
  val patternClass = pattern.resolve()?.rawType()
  return find {
    val supers = (it.resolve()?.allSupers()?.map(PsiClass::rawType) ?: emptyList()) + it.rawType()
    supers.filter { clazz -> clazz.name != "Object" }.contains(patternClass)
  } ?: pattern
}

private fun PsiType.isGroovyLangObject(): Boolean = equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)

private fun PsiType.mapConjuncts(action: (PsiType) -> PsiType): PsiType {
  return when (this) {
    is PsiIntersectionType -> createIntersection(conjuncts.map(action))
    else -> action(this)
  }
}

private class TypeLattice(context: PsiElement) {
  private val manager = context.manager
  private val top = getJavaLangObject(context) as PsiType
  private val bottom = PsiType.NULL as PsiType

  fun join(types: Iterable<PsiType>): PsiType = types.fold(bottom) { accum, type ->
    GenericsUtil.getLeastUpperBound(accum, type, manager) ?: bottom
  }

  fun meet(types: Iterable<PsiType>): PsiType = types.fold(top) { accum, type ->
    GenericsUtil.getGreatestLowerBound(accum, type)
  }
}


private fun PsiClass.allSupers(): Set<PsiClass> {
  return supers.flatMap { it.allSupers() }.union(supers.asList()).toSet()
}