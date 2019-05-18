// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder

/**
 * @author knisht
 */

/**
 * Represents graph which is used for determining [InferenceUnit] dependencies.
 */
class InferenceUnitGraph(private val registry: InferenceUnitRegistry) {
  private val representativeMap: MutableMap<InferenceUnit, InferenceUnit> = LinkedHashMap()

  /**
   * Runs inference between nodes in the graph/
   */
  init {
    condensate(registry)
    val order = topologicalOrder()
    setTreeStructure(order)
    collapseTreeEdges(order)
    propagatePossibleInstantiations(order)
  }

  /**
   * Handles cyclic dependencies.
   * If there is cyclic dependency among units, than all these units represent one type and can be merged.
   * [representativeMap] is a correspondence between units and their common chosen one, named representative.
   */
  private fun condensate(registry: InferenceUnitRegistry) {
    val nodeMap = LinkedHashMap<InferenceUnit, InferenceGraphNode>()
    registry.getUnits().forEach { nodeMap[it] = InferenceGraphNode(it); }

    for (unit in registry.getUnits()) {
      val node = nodeMap[unit]!!
      for (dependency in unit.supertypes.map { nodeMap[it] }) {
        node.addDependency(dependency ?: continue)
      }
      for (dependency in unit.subtypes.map { nodeMap[it] }) {
        dependency?.addDependency(node)
      }
    }

    val components = InferenceVariablesOrder.initNodes(nodeMap.values).map { it.value!! }

    for (component in components) {
      val representative = component.sortedBy { it.toString() }.first()
      component.forEach { representativeMap[it] = representative }
    }
    val transformTypesToRepresentatives: (InferenceUnit, InferenceUnit) -> InferenceUnit? = { anchor, target ->
      val representative = getRepresentative(target)
      if (representative == getRepresentative(anchor)) {
        null
      }
      else {
        representative
      }
    }
    registry.getUnits().forEach { unit ->
      val mappedSupertypes = unit.supertypes.mapNotNull { transformTypesToRepresentatives(unit, it) }
      unit.supertypes.clear()
      unit.supertypes.addAll(mappedSupertypes)
      val mappedSubtypes = unit.subtypes.mapNotNull { transformTypesToRepresentatives(unit, it) }
      unit.subtypes.clear()
      unit.subtypes.addAll(mappedSubtypes)
    }
  }


  /**
   * Sorts units in topological order. Requires absence of cyclic dependencies.
   */
  private fun topologicalOrder(): List<InferenceUnit> {
    val visited = HashSet<InferenceUnit>()
    val order = ArrayList<InferenceUnit>()
    for (node in getRepresentatives()) {
      if (node !in visited) {
        traverseGraph(node, visited, order)
      }
    }
    order.reversed().forEach { node ->
      for (dependentNode in node.subtypes) {
        dependentNode.unitInstantiation = node
      }
    }
    return order.reversed()
  }

  private fun traverseGraph(node: InferenceUnit,
                            visited: MutableSet<InferenceUnit>,
                            order: MutableList<InferenceUnit>) {
    visited.add(node)
    for (dependentNode in node.subtypes + node.weakSubtypes) {
      if (dependentNode !in visited) {
        traverseGraph(dependentNode, visited, order)
      }
    }
    order.add(node)
  }

  /**
   * Rearranges graph to tree.
   * Each unit may have several supertypes. A path from the unit to above is called "branch".
   * Note, that java inheritance system is in fact a tree. It means that if we know that T <: U and T <: S, than U <: S or S <: U.
   * So in fact this method just creates a relation between S and U.
   * This transformation saves requirements T <: U and T <: S and therefore we can determine unique parent of T.
   * If there were no relation between S and U, it is undefined whether it will appear S <: U or U <: S.
   */
  private fun setTreeStructure(order: List<InferenceUnit>) {
    for (unit in order.filter { it.supertypes.size > 1 }) {
      val branches = unit.supertypes.toList()
      var lastUniqueBranchIndex = 0
      val parentUnits = collectParents(branches[lastUniqueBranchIndex]).toMutableSet()
      for (index in branches.indices - lastUniqueBranchIndex) {
        if (branches[index] !in parentUnits) {
          merge(branches[lastUniqueBranchIndex], branches[index], parentUnits)
          lastUniqueBranchIndex = index
          parentUnits.addAll(collectParents(branches[lastUniqueBranchIndex]))
        }
      }
      unit.unitInstantiation = branches[lastUniqueBranchIndex]
    }
  }

  /**
   * Constructs branch recursively by collecting parent units.
   */
  private fun collectParents(unit: InferenceUnit?): Set<InferenceUnit> {
    unit ?: return mutableSetOf()
    return collectParents(unit.unitInstantiation) + unit
  }

  /**
   * Merges two branches.
   * Goes up by branch of [mergingUnit] while all units from this branch are unique.
   * If there is no way further, so all branch of [mergingUnit] became connected to [anchorUnit].
   */
  private fun merge(anchorUnit: InferenceUnit, mergingUnit: InferenceUnit, commonUnits: Set<InferenceUnit>) {
    if (mergingUnit.unitInstantiation in commonUnits || mergingUnit.unitInstantiation == null) {
      mergingUnit.unitInstantiation = anchorUnit
    }
    else {
      merge(anchorUnit, mergingUnit.unitInstantiation!!, commonUnits)
    }
  }


  /**
   * If node has no dependencies, it is possible to remove parametrized type.
   */
  private fun propagatePossibleInstantiations(order: List<InferenceUnit>) {
    for (unit in order) {
      unit.run {
        val mayBeInstantiatedToSupertype = unitInstantiation != null && (subtypes + weakSubtypes).isEmpty()
        val isDetached = flexible && (subtypes + supertypes + weakSupertypes + weakSubtypes).isEmpty()
        val hasNoDependencies = unitInstantiation == null && subtypes.isEmpty()
        when {
          mayBeInstantiatedToSupertype -> {
            typeInstantiation = unitInstantiation!!.type
          }
          !(isDetached || hasNoDependencies) -> {
            forbidInstantiation = true
          }
        }
      }
    }
  }


  /**
   * Tree branches may be shortened, if some node has only one child.
   */
  private fun collapseTreeEdges(order: List<InferenceUnit>) {
    val internalNodeMap = LinkedHashMap<InferenceUnit, InternalNode>()
    order.forEach { internalNodeMap[it] = InternalNode(it) }
    order.map { internalNodeMap[it]!! }.forEach { internalNodeMap[it.next]?.children?.add(it) }
    for (unit in order) {
      val node = internalNodeMap[unit]!!
      if (node.children.size == 1 && node.next != null) {
        val child = node.children.first()
        val parent = internalNodeMap[node.next!!]!!
        child.node.unitInstantiation = node.next
        child.next = node.next
        parent.children.remove(node)
        parent.children.add(child)
        representativeMap[unit] = node.next!!
        representativeMap.entries.filter { it.value == unit }.forEach { it.setValue(node.next!!) }
      }
    }
  }

  data class InternalNode(val node: InferenceUnit) {
    var next = node.unitInstantiation
    val children = LinkedHashSet<InternalNode>()
  }


  fun getRepresentative(unit: InferenceUnit): InferenceUnit {
    return representativeMap[unit]!!
  }

  fun getRepresentatives(): Collection<InferenceUnit> {
    return representativeMap.values.toSet()
  }

  fun getEqualUnits(part: InferenceUnit): List<InferenceUnit> {
    return representativeMap.entries.mapNotNull { (unit, representative) ->
      if (representative != getRepresentative(part))
        null
      else
        unit
    }
  }

}