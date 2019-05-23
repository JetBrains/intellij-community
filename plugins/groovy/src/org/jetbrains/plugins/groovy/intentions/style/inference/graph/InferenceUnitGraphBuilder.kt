// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiType

class InferenceUnitGraphBuilder {

  private val relations: MutableList<Relation> = mutableListOf()
  private val registered: MutableSet<InferenceUnit> = mutableSetOf()
  private val fixedUnits: MutableSet<InferenceUnit> = mutableSetOf()
  private val fixedInstantiations: MutableMap<InferenceUnit, PsiType> = mutableMapOf()
  private val directUnits: MutableSet<InferenceUnit> = mutableSetOf()


  fun add(left: InferenceUnit, right: InferenceUnit, type: RelationType): InferenceUnitGraphBuilder {
    register(left)
    register(right)
    relations.add(Relation(left, right, type))
    relations.add(Relation(right, left, type.complement()))
    return this
  }

  fun register(unit: InferenceUnit): InferenceUnitGraphBuilder {
    registered.add(unit)
    return this
  }

  fun register(unitNode: InferenceUnitNode): InferenceUnitGraphBuilder {
    register(unitNode.core)
    if (unitNode.direct) {
      setDirect(unitNode.core)
    }
    if (unitNode.forbidInstantiation) {
      forbidInstantiation(unitNode.core)
    }
    setType(unitNode.core, unitNode.typeInstantiation)
    return this
  }

  fun forbidInstantiation(unit: InferenceUnit): InferenceUnitGraphBuilder {
    register(unit)
    fixedUnits.add(unit)
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
    val superTypesMap: MutableList<MutableSet<() -> InferenceUnitNode>> = mutableListOf()
    val subTypesMap: MutableList<MutableSet<() -> InferenceUnitNode>> = mutableListOf()
    //todo: bug with order
    val registeredUnits = registered.sortedBy { it.initialTypeParameter.name }
    repeat(registeredUnits.size) {
      superTypesMap.add(mutableSetOf())
      subTypesMap.add(mutableSetOf())
    }
    val unitIndexMap: Map<InferenceUnit, Int> = registeredUnits.zip(registeredUnits.indices).toMap()
    for ((left, right, type) in relations) {
      when (type) {
        RelationType.SUPER -> subTypesMap[unitIndexMap.getValue(left)].add { inferenceNodes[unitIndexMap.getValue(right)] }
        RelationType.SUB -> superTypesMap[unitIndexMap.getValue(left)].add { inferenceNodes[unitIndexMap.getValue(right)] }
      }
    }
    for (index in registeredUnits.indices) {
      val unit = registeredUnits[index]
      inferenceNodes.add(InferenceUnitNode(unit, superTypesMap[index], subTypesMap[index],
                                           fixedInstantiations[unit]
                                           ?: PsiType.NULL,
                                           unit in fixedUnits,
                                           unit in directUnits))
    }
    return InferenceUnitGraph(inferenceNodes)
  }

}