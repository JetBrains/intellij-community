// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import org.jetbrains.plugins.groovy.intentions.style.inference.InferenceGraphNode

/**
 * Represents graph which is used for determining [InferenceUnitNode] dependencies.
 */
data class InferenceUnitGraph(val units: List<InferenceUnitNode>) {

  fun dependsOnNode(type: PsiType): Boolean {
    return type in units.map { it.core.type }
  }

  /**
   * @return [units], sorted in topological order by [InferenceUnitNode.typeInstantiation]. Takes class parameters into consideration.
   */
  fun resolveOrder(): List<InferenceUnitNode> {
    val visited = mutableSetOf<InferenceUnitNode>()
    val order = mutableListOf<InferenceUnitNode>()
    for (unit in units) {
      visitTypes(unit, visited, order)
    }
    return order
  }

  private fun visitTypes(unit: InferenceUnitNode,
                         visited: MutableSet<InferenceUnitNode>,
                         order: MutableList<InferenceUnitNode>) {
    if (unit in visited) {
      return
    }
    visited.add(unit)
    units.find { it.type == unit.typeInstantiation }?.run { visitTypes(this, visited, order) }
    when (unit.typeInstantiation) {
      is PsiClassType -> unit.typeInstantiation.parameters
      is PsiIntersectionType -> unit.typeInstantiation.conjuncts
      else -> emptyArray()
    }.forEach { parameter ->
      units.find { it.type == parameter }?.run { visitTypes(this, visited, order) }
    }
    order.add(unit)
  }
}


/**
 * Runs inference process for nodes in the graph
 */
@Suppress("UnnecessaryVariable")
fun determineDependencies(graph: InferenceUnitGraph): InferenceUnitGraph {
  val condensedGraph = condense(graph)
  val sortedGraph = topologicalOrder(condensedGraph)
  val tree = setTreeStructure(sortedGraph)
  val collapsedTree = collapseTreeEdges(tree)
  return collapsedTree
}

/**
 * Handles cyclic dependencies.
 * If there is cyclic dependency among units, than all these units represent one type and can be merged.
 *
 * @return new condensed graph
 */
private fun condense(graph: InferenceUnitGraph): InferenceUnitGraph {
  val nodeMap = LinkedHashMap<InferenceUnitNode, InferenceGraphNode>()
  val representativeMap = mutableMapOf<InferenceUnit, InferenceUnit>()
  graph.units.forEach { nodeMap[it] = InferenceGraphNode(it); }

  for (unit in graph.units) {
    val node = nodeMap[unit]!!
    unit.supertypes.map { nodeMap[it]!! }.forEach { node.addDependency(it) }
    unit.subtypes.map { nodeMap[it]!! }.forEach { it.addDependency(node) }
  }

  val components = InferenceVariablesOrder.initNodes(nodeMap.values).map { it.value!! }
  val builder = InferenceUnitGraphBuilder()
  for (component in components) {
    val representative = component.minBy(InferenceUnitNode::toString)!!
    component.forEach {
      representativeMap[it.core] = representative.core
      if (it != representative) {
        builder.setType(it.core, representative.core.type).setDirect(it.core)
      }
    }
    if (component.any(InferenceUnitNode::forbiddenToInstantiate)) {
      builder.forbidInstantiation(representative.core)
    }
  }
  graph.units.filter { representativeMap[it.core] == it.core }.forEach { unit ->
    builder.register(unit)
    unit.supertypes.mapNotNull { getRepresentative(unit, it, representativeMap) }.forEach { builder.addRelation(it, unit.core) }
    unit.subtypes.mapNotNull { getRepresentative(unit, it, representativeMap) }.forEach { builder.addRelation(unit.core, it) }
  }
  return builder.build()
}


fun getRepresentative(anchor: InferenceUnitNode,
                      target: InferenceUnitNode,
                      representativeMap: Map<InferenceUnit, InferenceUnit>): InferenceUnit? {
  return representativeMap.getValue(target.core).run {
    if (this == representativeMap.getValue(anchor.core)) {
      null
    }
    else {
      this
    }
  }
}

/**
 * Sorts units in topological order. Requires absence of cyclic dependencies.
 *
 * @return ordered units
 */
private fun topologicalOrder(unitGraph: InferenceUnitGraph): InferenceUnitGraph {
  val visited = mutableSetOf<InferenceUnitNode>()
  val order = mutableListOf<InferenceUnitNode>()
  for (node in unitGraph.units) {
    if (node !in visited) {
      traverseGraph(node, visited, order)
    }
  }
  return InferenceUnitGraph(order.reversed())
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
 * Note, that java inheritance system for type parameters is in fact a tree.
 * It means that if we know that T <: U and T <: S, than U <: S or S <: U.
 * So this method just creates a relation between S and U.
 * This transformation saves requirements T <: U and T <: S and therefore we can determine unique parent of T.
 * If there were no relation between S and U, it is undefined whether it will appear S <: U or U <: S.
 */
private fun setTreeStructure(order: InferenceUnitGraph): InferenceUnitGraph {
  val parentMap = mutableMapOf<InferenceUnitNode, InferenceUnitNode>()
  order.units.forEach { node -> node.subtypes.forEach { parentMap[it] = node } }
  for (unit in order.units.filter { it.supertypes.size > 1 }) {
    val branches = unit.supertypes.toList()
    var rootBranch = branches.first()
    val processedUnits = collectParents(rootBranch, parentMap).toMutableSet()
    for (currentBranch in branches - rootBranch) {
      if (currentBranch !in processedUnits) {
        merge(rootBranch, currentBranch, processedUnits, parentMap)
        rootBranch = currentBranch
        processedUnits.addAll(collectParents(rootBranch, parentMap))
      }
    }
    parentMap[unit] = rootBranch
  }
  val builder = InferenceUnitGraphBuilder()
  for (unit in order.units) {
    builder.register(unit)
    parentMap[unit]?.run { builder.addRelation(this.core, unit.core) }
  }
  return builder.build()
}

/**
 * Constructs branch recursively by collecting parent units.
 */
private fun collectParents(unit: InferenceUnitNode?,
                           parentMap: Map<InferenceUnitNode, InferenceUnitNode>): MutableSet<InferenceUnitNode> {
  return if (unit == null) {
    mutableSetOf()
  }
  else {
    collectParents(parentMap[unit], parentMap).apply { add(unit) }
  }
}

/**
 * Merges two branches.
 * Goes up by branch of [mergingUnit] while all units from this branch are unique (not in [commonUnits]).
 * If there is no way further, entire branch of [mergingUnit] became connected to [anchorUnit].
 */
private tailrec fun merge(anchorUnit: InferenceUnitNode,
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
 * Tree branches may be shortened for nodes representing covariant types
 */
private fun collapseTreeEdges(unitGraph: InferenceUnitGraph): InferenceUnitGraph {
  val builder = InferenceUnitGraphBuilder()
  val instantiations = mutableMapOf<InferenceUnit, PsiType>()

  for (unit in unitGraph.units) {
    if (unit.typeInstantiation != PsiType.NULL && !unitGraph.dependsOnNode(unit.typeInstantiation)) {
      var currentUnit: InferenceUnitNode? = unit
      while (currentUnit?.parent != null) {
        currentUnit = currentUnit.parent
      }
      instantiations[currentUnit!!.core] = unit.typeInstantiation
    }
  }
  for (unit in unitGraph.units) {
    builder.register(unit)
    unit.supertypes.forEach { builder.addRelation(it.core, unit.core) }
    run {
      builder.setType(unit.core, instantiations[unit.core] ?: return@run)
    }
  }
  return builder.build()
}
