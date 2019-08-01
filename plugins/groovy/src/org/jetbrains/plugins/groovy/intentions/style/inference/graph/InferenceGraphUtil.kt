// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_VOID
import com.intellij.psi.PsiIntersectionType.createIntersection
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.flattenIntersections
import org.jetbrains.plugins.groovy.intentions.style.inference.getInferenceVariable
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


fun createGraphFromInferenceVariables(session: GroovyInferenceSession,
                                      virtualMethod: GrMethod,
                                      forbiddingTypes: Collection<PsiType>,
                                      usageInformation: TypeUsageInformation,
                                      constantTypes: List<PsiTypeParameter>): Pair<InferenceUnitGraph, List<PsiType>> {
  val variableMap = BidirectionalMap<InferenceUnit, InferenceVariable>()
  val builder = InferenceUnitGraphBuilder()
  val constantNames = constantTypes.map { it.name }.toMutableList()
  val flexibleTypes = virtualMethod.parameters.map { it.type }
  val variables = virtualMethod.typeParameters.mapNotNull { getInferenceVariable(session, it.type()) }
  val strictInstantiations = mutableMapOf<InferenceVariable, PsiClass>()
  for (variable in variables) {
    val requiredTypes = usageInformation.requiredClassTypes[variable.parameter.type().typeParameter()] ?: emptyList()
    val strictInstantiation = findStrictInstantiation(requiredTypes) ?: continue
    val extendingVariables = variables.filter { it.delegate.extendsListTypes.singleOrNull()?.resolve() == variable.delegate }
    extendingVariables.forEach {
      strictInstantiations[it] = strictInstantiation
    }
    strictInstantiations[variable] = strictInstantiation
  }
  val setTypes = mutableListOf<PsiClassType>()
  for (variable in variables) {
    val variableType = variable.parameter.type()
    val superTypes = usageInformation.requiredClassTypes[variableType.typeParameter()]?.filter { it.marker == CONTAINS }?.map { it.clazz }
                     ?: emptyList()
    val subTypes = usageInformation.requiredClassTypes[variableType.typeParameter()]?.filter { it.marker == LOWER }?.map { it.clazz }
                   ?: emptyList()
    val signatureTypes = variable.delegate.extendsList.referencedTypes.toList()
    val instantiation = completeInstantiation(variable, session, usageInformation, superTypes, subTypes, signatureTypes, setTypes)
    val core = InferenceUnit(variable.parameter,
                             flexible = variableType in flexibleTypes,
                             constant = variableType.name in constantNames)
    if (variableType.name !in constantNames) {
      builder.setType(core, instantiation)
    }
    else {
      builder.setType(core, variable.parameter.extendsListTypes.firstOrNull() ?: PsiType.NULL)
    }
    if (variableType in forbiddingTypes) {
      builder.forbidInstantiation(core)
    }
    if (strictInstantiations.containsKey(variable)) {
      // todo: this block cancels almost all computations above, it should be places higher for faster computing
      builder.setDirect(core)
      builder.setType(core, strictInstantiations[variable]!!.type())
    }
    variableMap[core] = variable
  }
  for ((unit, variable) in variableMap.entries.sortedBy { it.key.toString() }) {
    val upperBounds = variable.getBounds(InferenceBound.UPPER) + variable.getBounds(InferenceBound.EQ)
    deepConnect(session, variableMap, upperBounds) { builder.addRelation(it, unit) }
    val lowerBounds = variable.getBounds(InferenceBound.LOWER) + variable.getBounds(InferenceBound.EQ)
    deepConnect(session, variableMap, lowerBounds) { builder.addRelation(unit, it) }
  }
  return builder.build() to setTypes
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

fun findStrictInstantiation(constraints: Collection<BoundConstraint>): PsiClass? {
  val strictTypes = constraints.mapNotNull {
    when (it.marker) {
      EQUAL -> it.clazz
      else -> null
    }
  }
  return strictTypes.maxWith(Comparator { left, right ->
    if (TypesUtil.canAssign(left.type(), right.type(), left, METHOD_PARAMETER) == OK) 1 else -1
  })
}


fun completeInstantiation(variable: InferenceVariable,
                          session: GroovyInferenceSession,
                          usageInformation: TypeUsageInformation,
                          superTypes: List<PsiClass>,
                          subTypes: List<PsiClass>,
                          signatureTypes: List<PsiClassType>,
                          setTypes: MutableList<PsiClassType>): PsiType {
  val javaLangObject = PsiType.getJavaLangObject(session.context.manager, GlobalSearchScope.allScope(session.context.project)) as PsiType
  val variableType = variable.delegate.type()
  val contravariantTypes = usageInformation.run {
    contravariantTypes.subtract(this.covariantTypes).subtract(dependentTypes.map { it.type() })
  }
  val covariantTypes = usageInformation.run {
    covariantTypes.subtract(this.contravariantTypes).subtract(dependentTypes.map { it.type() })
  }
  return when (variableType) {
    in contravariantTypes -> {
      val lowerBound = when {
        variable.parameter in usageInformation.inhabitedTypes -> usageInformation.inhabitedTypes.getValue(variable.parameter).fold(
          javaLangObject) { acc, clazz ->
          greatestLowerBound(acc, clazz.type())
        }
        else -> subTypes.foldRight(PsiType.NULL as PsiType) { clazz, acc ->
          GenericsUtil.getLeastUpperBound(acc, clazz.type(), session.context.manager)!!
        }
      }

      val directLowerBound = when (lowerBound) {
        is PsiIntersectionType -> createIntersection(
          lowerBound.conjuncts.map { lowerType -> signatureTypes.find { it.resolve() == lowerType.resolve() } ?: lowerType })
        else -> signatureTypes.find { it.resolve() == lowerBound.resolve() } ?: lowerBound
      }
      when {
        superTypes.isNotEmpty() -> createIntersection(
          superTypes.map { upperType -> signatureTypes.find { it.resolve() == upperType } ?: upperType.type() })
        directLowerBound != PsiType.NULL -> PsiWildcardType.createSuper(session.context.manager, directLowerBound)
        else -> PsiWildcardType.createUnbounded(session.context.manager)
      }
    }
    in covariantTypes -> {
      val upperBound = superTypes
        .foldRight(javaLangObject) { clazz, acc -> greatestLowerBound(clazz.type(), acc) }
      val fixedUpperBound = if (upperBound == javaLangObject && usageInformation.inhabitedTypes[variable.parameter]?.isNotEmpty() == true) {
        usageInformation.inhabitedTypes.getValue(variable.parameter).foldRight(
          PsiType.NULL as PsiType) { clazz, acc -> GenericsUtil.getLeastUpperBound(clazz.type(), acc, session.context.manager)!! }
      }
      else upperBound
      val directUpperBound = when (fixedUpperBound) {
        is PsiIntersectionType -> createIntersection(
          fixedUpperBound.conjuncts.map { llb -> signatureTypes.find { it.resolve() == llb.resolve() } ?: llb })
        else -> signatureTypes.find { it.resolve() == fixedUpperBound.resolve() } ?: fixedUpperBound
      }
      if (directUpperBound != javaLangObject) PsiWildcardType.createExtends(session.context.manager, directUpperBound)
      else PsiWildcardType.createUnbounded(session.context.manager)
    }
    else -> {
      val lowerBound = superTypes.foldRight(javaLangObject) { clazz, acc -> greatestLowerBound(acc, clazz.type()) }
      val upperBound = subTypes.foldRight(PsiType.NULL as PsiType)
      { clazz, acc -> GenericsUtil.getLeastUpperBound(clazz.type(), acc, session.context.manager)!! }
        .run {
          when (this) {
            is PsiIntersectionType -> createIntersection(conjuncts.filter { !it.isGroovyLangObject() })
            else -> if (isGroovyLangObject()) javaLangObject else this
          }
        }
      val invariantUpperBound = if (lowerBound == javaLangObject && upperBound !is PsiIntersectionType) upperBound else lowerBound
      val accepted = when (invariantUpperBound) {
        is PsiIntersectionType -> createIntersection(
          invariantUpperBound.conjuncts.map { invariantType ->
            signatureTypes.find {
              ((it.resolve()?.allSupers() ?: emptySet()) + it.resolve()).subtract(listOf(javaLangObject.resolve())).contains(
                invariantType.resolve())
            } ?: invariantType
          })
        else -> signatureTypes.find {
          ((it.resolve()?.allSupers() ?: emptySet()) + it.resolve()).subtract(listOf(javaLangObject.resolve())).contains(
            invariantUpperBound.resolve())
        } ?: invariantUpperBound
      }.filterImplementedInterfaces()
      if (superTypes.union(subTypes).run { any { it != first() } }) {
        setTypes.add(variableType)
      }
      if (accepted.equalsToText(JAVA_LANG_VOID)) PsiWildcardType.createUnbounded(session.manager) else accepted
    }
  }
}


private fun PsiType.isGroovyLangObject(): Boolean = equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)

fun greatestLowerBound(typeA: PsiType, typeB: PsiType): PsiType {
  return GenericsUtil.getGreatestLowerBound(typeA, typeB)
}

private fun PsiType.filterImplementedInterfaces(): PsiType {
  if (this !is PsiIntersectionType) {
    return this
  }
  val resolvedClasses = this.conjuncts.map { it.resolve()!! }
  val remainedInterfaces = resolvedClasses.filter { clazz -> !(clazz.isInterface && resolvedClasses.any { it.supers.contains(clazz) }) }
  return createIntersection(this.conjuncts.filter { it.resolve() in remainedInterfaces })
}

private fun PsiClass.allSupers(): Set<PsiClass> {
  return supers.flatMap { it.allSupers() }.union(supers.asList()).toSet()
}