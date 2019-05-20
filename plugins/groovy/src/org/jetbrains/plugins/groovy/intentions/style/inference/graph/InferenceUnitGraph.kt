// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import org.jetbrains.plugins.groovy.intentions.style.inference.InferenceGraphNode

/**
 * @author knisht
 */


/**
 * Represents graph which is used for determining [InferenceUnitNode] dependencies.
 */
data class InferenceUnitGraph(val units: List<InferenceUnitNode>)


/**
 * Runs inference between nodes in the graph/
 */
fun determineDependencies(graph: InferenceUnitGraph): InferenceUnitGraph {
  val (unitGraph, representativeMap) = condensate(graph)
  val resultGraph = propagatePossibleInstantiations(collapseTreeEdges(setTreeStructure(topologicalOrder(unitGraph))))
  val builder = InferenceUnitGraphBuilder()
  resultGraph.units.forEach { unit ->
    builder.register(unit)
    unit.subtypes.forEach {
      builder.add(unit.core, it.core, RelationType.SUPER)
    }
  }
  representativeMap.forEach { (core, representative) ->
    if (core != representative) {
      builder.register(core).setType(core, representativeMap.getValue(core).type).setDirect(core)
    }
  }
  return builder.build()
}

/**
 * Handles cyclic dependencies.
 * If there is cyclic dependency among units, than all these units represent one type and can be merged.
 *
 * @return new condensated graph and mapping between nodes and their new parent
 */
private fun condensate(graph: InferenceUnitGraph): Pair<InferenceUnitGraph, Map<InferenceUnit, InferenceUnit>> {
  val nodeMap = LinkedHashMap<InferenceUnitNode, InferenceGraphNode>()
  val representativeMap = mutableMapOf<InferenceUnit, InferenceUnit>()
  graph.units.forEach { nodeMap[it] = InferenceGraphNode(it); }

  for (unit in graph.units) {
    val node = nodeMap[unit]!!
    for (dependency in unit.supertypes.map { nodeMap[it]!! }) {
      node.addDependency(dependency)
    }
    for (dependency in unit.subtypes.map { nodeMap[it]!! }) {
      dependency.addDependency(node)
    }
  }

  val components = InferenceVariablesOrder.initNodes(nodeMap.values).map { it.value!! }

  val builder = InferenceUnitGraphBuilder()
  for (component in components) {
    val representative = component.sortedBy { it.toString() }.first()
    if (component.size > 1) {
      builder.forbidInstantiation(representative.core)
    }
    component.forEach {
      representativeMap[it.core] = representative.core
      if (it != representative) {
        builder.setType(it.core, representative.core.type).setDirect(it.core)
      }
    }
  }
  graph.units.forEach { unit ->
    builder.register(unit)
    unit.supertypes.mapNotNull { transformTypesToRepresentatives(unit, it, representativeMap) }.forEach {
      builder.add(it, unit.core, RelationType.SUPER)
    }
    unit.subtypes.mapNotNull { transformTypesToRepresentatives(unit, it, representativeMap) }.forEach {
      builder.add(it, unit.core, RelationType.SUB)
    }
  }
  return Pair(builder.build(), representativeMap)
}


fun transformTypesToRepresentatives(anchor: InferenceUnitNode,
                                    target: InferenceUnitNode,
                                    representativeMap: Map<InferenceUnit, InferenceUnit>): InferenceUnit? {
  val representative = representativeMap.getValue(target.core)
  if (representative == representativeMap.getValue(anchor.core)) {
    return null
  }
  else {
    return representative
  }
}

/**
 * Sorts units in topological order. Requires absence of cyclic dependencies.
 *
 * @return ordered units
 */
private fun topologicalOrder(unitGraph: InferenceUnitGraph): List<InferenceUnitNode> {
  val visited = mutableSetOf<InferenceUnitNode>()
  val order = mutableListOf<InferenceUnitNode>()
  for (node in unitGraph.units) {
    if (node !in visited) {
      traverseGraph(node, visited, order)
    }
  }
  return order.reversed()
}

private fun traverseGraph(node: InferenceUnitNode,
                          visited: MutableSet<InferenceUnitNode>,
                          order: MutableList<InferenceUnitNode>) {
  visited.add(node)
  for (dependentNode in node.subtypes) {
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
 * So this method just creates a relation between S and U.
 * This transformation saves requirements T <: U and T <: S and therefore we can determine unique parent of T.
 * If there were no relation between S and U, it is undefined whether it will appear S <: U or U <: S.
 */
private fun setTreeStructure(order: List<InferenceUnitNode>): InferenceUnitGraph {
  val parentMap = mutableMapOf<InferenceUnitNode, InferenceUnitNode>()
  order.forEach { node ->
    for (dependentNode in node.subtypes) {
      parentMap[dependentNode] = node
    }
  }
  for (unit in order.filter { it.supertypes.size > 1 }) {
    val branches = unit.supertypes.toList()
    var lastUniqueBranchIndex = 0
    val processedUnits = collectParents(branches[lastUniqueBranchIndex], parentMap).toMutableSet()
    for (index in branches.indices - lastUniqueBranchIndex) {
      if (branches[index] !in processedUnits) {
        merge(branches[lastUniqueBranchIndex], branches[index], processedUnits, parentMap)
        lastUniqueBranchIndex = index
        processedUnits.addAll(collectParents(branches[lastUniqueBranchIndex], parentMap))
      }
    }
    parentMap[unit] = branches[lastUniqueBranchIndex]
  }
  val builder = InferenceUnitGraphBuilder()
  for (unit in order) {
    (parentMap[unit])?.run {
      builder.add(unit.core, this.core, RelationType.SUB)
    }
  }
  order.forEach { builder.register(it) }
  return builder.build()
}

/**
 * Constructs branch recursively by collecting parent units.
 */
private fun collectParents(unit: InferenceUnitNode?,
                           parentMap: Map<InferenceUnitNode, InferenceUnitNode>): Set<InferenceUnitNode> {
  unit ?: return mutableSetOf()
  return collectParents(parentMap[unit], parentMap) + unit
}

/**
 * Merges two branches.
 * Goes up by branch of [mergingUnit] while all units from this branch are unique.
 * If there is no way further, so all branch of [mergingUnit] became connected to [anchorUnit].
 */
private fun merge(anchorUnit: InferenceUnitNode,
                  mergingUnit: InferenceUnitNode,
                  commonUnits: Set<InferenceUnitNode>,
                  parentMap: MutableMap<InferenceUnitNode, InferenceUnitNode>) {
  if (parentMap[mergingUnit] in commonUnits || parentMap[mergingUnit] == null) {
    parentMap[mergingUnit] = anchorUnit
  }
  else {
    merge(anchorUnit, parentMap.getValue(mergingUnit), commonUnits, parentMap)
  }
}


/**
 * Tree branches may be shortened, if some node has only one child or has one parent and one child
 */
private fun collapseTreeEdges(unitGraph: InferenceUnitGraph): InferenceUnitGraph {
  val internalNodeMap = LinkedHashMap<InferenceUnitNode, InternalNode>()
  val collapsedNodesMap = mutableMapOf<InferenceUnit, InferenceUnit>()
  unitGraph.units.forEach {
    internalNodeMap[it] = InternalNode(it)
  }
  unitGraph.units.map { internalNodeMap[it]!! }.forEach { internalNodeMap[it.parent]?.children?.add(it) }
  for (unit in unitGraph.units) {
    val node = internalNodeMap[unit]!!
    if (node.children.size == 1 && node.parent != null) {
      node.removed = true
      val childNode = node.children.first()
      val parentNode = internalNodeMap[node.parent!!]!!
      childNode.parent = node.parent
      parentNode.children.remove(node)
      parentNode.children.add(childNode)
      collapsedNodesMap[unit.core] = node.parent!!.core
      collapsedNodesMap.entries.filter { it.value == unit.core }.forEach { it.setValue(node.parent!!.core) }
    }
  }
  val builder = InferenceUnitGraphBuilder()
  internalNodeMap.values.filter { !it.removed }.forEach { node ->
    builder.register(node.unitNode)
    if (node.parent != null) {
      builder.add(internalNodeMap[node.parent!!]!!.unitNode.core, node.unitNode.core,
                  RelationType.SUPER)
    }
  }
  collapsedNodesMap.forEach { (unit, representative) -> builder.register(unit).setDirect(unit).setType(unit, representative.type) }
  return builder.build()
}

data class InternalNode(val unitNode: InferenceUnitNode) {
  var parent = unitNode.parent
  var removed = false
  val children = LinkedHashSet<InternalNode>()
}


/**
 * If node has no dependencies, it is possible to remove parametrized type.
 */
private fun propagatePossibleInstantiations(unitGraph: InferenceUnitGraph): InferenceUnitGraph {
  val builder = InferenceUnitGraphBuilder()
  unitGraph.units.forEach {
    builder.register(it)
  }
  for (unit in unitGraph.units) {
    unit.run {
      val mayBeInstantiatedToSupertype = parent != null && subtypes.isEmpty()
      val isDetached = core.flexible && (subtypes + supertypes).isEmpty()
      val hasNoDependencies = parent == null && subtypes.isEmpty()
      if (mayBeInstantiatedToSupertype) {
        builder.setType(core, parent!!.type)
      }
      else if (!(isDetached || hasNoDependencies)) {
        builder.forbidInstantiation(core)
      }
      Unit
    }
    unit.parent?.run {
      builder.add(unit.core, this.core, RelationType.SUB)
    }
  }
  return builder.build()
}
