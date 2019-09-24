// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiType

class InferenceUnitGraphBuilder {

  private val relations: MutableList<Pair<InferenceUnit, InferenceUnit>> = mutableListOf()
  private val registered: MutableSet<InferenceUnit> = mutableSetOf()
  private val fixedInstantiations: MutableMap<InferenceUnit, PsiType> = mutableMapOf()
  private val directUnits: MutableSet<InferenceUnit> = mutableSetOf()


  /**
   * Creates a relation between units (left is a supertype of right)
   */
  fun addRelation(left: InferenceUnit, right: InferenceUnit): InferenceUnitGraphBuilder {
    register(left)
    register(right)
    relations.add(left to right)
    return this
  }

  fun register(unit: InferenceUnit): InferenceUnitGraphBuilder {
    registered.add(unit)
    return this
  }

  fun register(unitNode: InferenceUnitNode): InferenceUnitGraphBuilder {
    if (unitNode.direct) {
      setDirect(unitNode.core)
    }
    setType(unitNode.core, unitNode.typeInstantiation)
    return this
  }


  fun setDirect(unit: InferenceUnit): InferenceUnitGraphBuilder {
    register(unit)
    directUnits.add(unit)
    return this
  }

  fun setType(unit: InferenceUnit, type: PsiType): InferenceUnitGraphBuilder {
    register(unit)
    fixedInstantiations[unit] = type
    return this
  }


  fun build(): InferenceUnitGraph {
    val inferenceNodes = ArrayList<InferenceUnitNode>()
    val superTypesMap = mutableListOf<MutableSet<() -> InferenceUnitNode>>()
    val subTypesMap = mutableListOf<MutableSet<() -> InferenceUnitNode>>()
    val registeredUnits = registered.sortedBy { it.initialTypeParameter.name }
    repeat(registeredUnits.size) {
      superTypesMap.add(mutableSetOf())
      subTypesMap.add(mutableSetOf())
    }
    val unitIndexMap = registeredUnits.zip(registeredUnits.indices).toMap()
    for ((left, right) in relations) {
      subTypesMap[unitIndexMap.getValue(left)].add { inferenceNodes[unitIndexMap.getValue(right)] }
      superTypesMap[unitIndexMap.getValue(right)].add { inferenceNodes[unitIndexMap.getValue(left)] }
    }
    for ((unit, index) in unitIndexMap) {
      inferenceNodes.add(InferenceUnitNode(unit, superTypesMap[index], subTypesMap[index],
                                           fixedInstantiations[unit] ?: PsiType.NULL,
                                           direct = unit in directUnits))
    }
    return InferenceUnitGraph(inferenceNodes)
  }

}