// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * @author knisht
 */

/**
 * Represents graph which is used for handling inference variables dependencies.
 * NB: graph should be built from variables that are already grouped by equality relation.
 */
class InferenceVariableGraph(components: List<List<InferenceVariable>>, private val session: GroovyInferenceSession) {

  private val representativeMap: MutableMap<InferenceVariable, InferenceVariable> = LinkedHashMap()
  // nodes is a map from representative to it's node
  val nodes: MutableMap<InferenceVariable, InferenceVariableNode> = LinkedHashMap()
  val initialVariableInstantiations: MutableMap<InferenceVariable, PsiType> = LinkedHashMap()

  /**
   * Performs initialization of the graph.
   * Firstly, there is one representative variable chosen for each group of equal inference variables.
   * Then it is converted to node and used for collecting dependencies on other nodes.
   * Secondly, all nodes are arranged in topological order, so inference will be performed from top to down.
   * This is required because we need to support valid structure of parents for processing dependent nodes.
   * Thirdly, graph is rearranged from generic acyclic graph to tree. This happens because java does not support multiple inheritance.
   * Fourthly, all detached nodes are instantiated in their specific type.
   */
  init {
    components.flatten().forEach { initialVariableInstantiations[it] = it.instantiation; it.instantiation = PsiType.NULL }
    for (component in components) {
      val representative = component[0]
      nodes[representative] = InferenceVariableNode(representative)
      component.forEach { representativeMap[it] = representative }
    }
    representativeMap.values.forEach {
      nodes[it]!!.collectDependencies(it, session, nodes)
    }
    val order = topologicalOrder()
    order.forEach { node ->
      for (dependentNode in node.subtypes) {
        dependentNode.directParent = node
      }
    }
    makeTree(order)
    propagatePossibleInstantiations(order)
  }

  private fun topologicalOrder(): List<InferenceVariableNode> {
    val visited = HashSet<InferenceVariableNode>()
    val order = ArrayList<InferenceVariableNode>()
    for (node in nodes.values) {
      if (node !in visited) {
        traverseGraph(node, visited, order)
      }
    }
    return order.reversed()
  }

  private fun traverseGraph(node: InferenceVariableNode,
                            visited: MutableSet<InferenceVariableNode>,
                            order: MutableList<InferenceVariableNode>) {
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
   * Each node may have several supertypes. A path from the node to above (by [InferenceVariableNode.directParent]) is called "branch".
   * Note, that java inheritance system is in fact a tree. It means that if we know that T <: U and T <: S, than U <: S or S <: U.
   * So in fact this method just creates a relation between S and U.
   * This transformation saves requirements T <: U and T <: S and therefore we can determine unique parent of T.
   * If there were no relation between S and U, it is undefined whether it will appear S <: U or U <: S.
   */
  private fun makeTree(order: List<InferenceVariableNode>) {
    for (node in order.filter { it.supertypes.size > 1 }) {
      val branches = node.supertypes.toList()
      val parentNodes = mutableSetOf<InferenceVariableNode>()
      var lastUniqueBranchIndex = 0
      parentNodes.addAll(collectParents(branches[lastUniqueBranchIndex]))
      for (index in branches.indices - lastUniqueBranchIndex) {
        if (branches[index] !in parentNodes) {
          merge(branches[lastUniqueBranchIndex], branches[index], parentNodes)
          lastUniqueBranchIndex = index
          parentNodes.addAll(collectParents(branches[lastUniqueBranchIndex]))
        }
      }
      node.directParent = branches[lastUniqueBranchIndex]
    }
  }


  private fun collectParents(node: InferenceVariableNode?): MutableSet<InferenceVariableNode> {
    if (node == null) {
      return mutableSetOf()
    }
    else {
      val parents = collectParents(node.directParent)
      parents.add(node)
      return parents
    }
  }

  private fun merge(anchorNode: InferenceVariableNode, mergingNode: InferenceVariableNode, commonNodes: Set<InferenceVariableNode>) {
    if (mergingNode.directParent in commonNodes || mergingNode.directParent == null) {
      mergingNode.directParent = anchorNode
    }
    else {
      merge(anchorNode, mergingNode.directParent!!, commonNodes)
    }
  }


  /**
   * If node has no dependencies, it is possible to remove parametrized type.
   */
  private fun propagatePossibleInstantiations(order: List<InferenceVariableNode>) {
    var instantiationSubstitutor = PsiSubstitutor.EMPTY
    for (node in order) {
      val variable = node.inferenceVariable
      if (node.supertypes.isEmpty() && node.supertypes.isEmpty()) {
        val validInstantiation = variable.upperBounds().firstOrNull { it is PsiClassType && it.hasParameters() }
        variable.instantiation = instantiationSubstitutor.substitute(validInstantiation ?: PsiType.NULL)
      }
      if (variable.instantiation != PsiType.NULL) {
        instantiationSubstitutor = instantiationSubstitutor.put(variable, variable.instantiation)
      }
    }
  }


  /**
   * Allows to collapse redundant type parameter if it represents parameter of the [method]
   * Example: `def <T, U> void foo(List<T> ts, U u)` {}. Here U will be flexible type parameter and may be removed.
   */
  fun adjustFlexibleVariables(method: GrMethod) {
    for (parameter in method.parameters) {
      val variable = getRepresentative(session.getInferenceVariable(session.substituteWithInferenceVariables(parameter.type)))
      if (variable != null) {
        val node = nodes[variable]!!
        if ((node.subtypes + node.supertypes + node.weakSupertypes + node.weakSubtypes).isEmpty()) {
          variable.instantiation = initialVariableInstantiations[variable]
        }
        else if (node.directParent != null) {
          variable.instantiation = node.directParent?.inferenceVariable?.type() ?: PsiType.NULL
        }
      }
    }
  }

  fun getParent(variable: InferenceVariable): InferenceVariable? {
    return nodes[variable]?.directParent?.inferenceVariable
  }


  fun getRepresentative(variable: InferenceVariable?): InferenceVariable? {
    return representativeMap[variable ?: return null]
  }

}