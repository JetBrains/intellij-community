// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.InferenceDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.ensureWildcards
import org.jetbrains.plugins.groovy.intentions.style.inference.flattenIntersections
import org.jetbrains.plugins.groovy.intentions.style.inference.getInferenceVariable
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


fun createGraphFromInferenceVariables(session: GroovyInferenceSession,
                                      driver: InferenceDriver,
                                      usageInformation: TypeUsageInformation): InferenceUnitGraph {
  val variableMap = BidirectionalMap<InferenceUnit, InferenceVariable>()
  val builder = InferenceUnitGraphBuilder()
  val constantNames = driver.defaultTypeParameterList.typeParameters.map { it.name }
  val flexibleTypes = driver.virtualMethod.parameters.map { it.type }
  val forbiddingTypes =
    usageInformation.contravariantTypes +
    driver.virtualMethod.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType } +
    driver.varargParameters.map { it.type }
  val variables = driver.virtualMethod.typeParameters.map { getInferenceVariable(session, it.type()) }

  for (variable in variables) {
    val variableType = variable.parameter.type()
    val extendsTypes = variable.parameter.extendsList.referencedTypes
    val residualExtendsTypes = when {
      extendsTypes.size <= 1 -> extendsTypes.toList()
      else -> extendsTypes.filter { usageInformation.requiredClassTypes[variableType.typeParameter()]?.contains(it.resolve()) ?: false }
    }
    val filteredType = when {
      residualExtendsTypes.size > 1 -> PsiIntersectionType.createIntersection(residualExtendsTypes.toList())
      residualExtendsTypes.isEmpty() && variable.instantiation is PsiIntersectionType -> PsiType.getJavaLangObject(session.manager,
                                                                                                                   session.scope)
      // questionable conditions. I guess, we can allow LUB instead of Object if variable's inferred type is simple (not an intersection).
      else -> residualExtendsTypes.firstOrNull() ?: variable.instantiation!!.ensureWildcards(
        GroovyPsiElementFactory.getInstance(variable.project), variable.manager)
    }
    val core = InferenceUnit(variable.parameter,
                             flexible = variableType in flexibleTypes,
                             constant = variableType.name in constantNames)
    builder.setType(core, filteredType)
    if (variableType in forbiddingTypes) {
      builder.forbidInstantiation(core)
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