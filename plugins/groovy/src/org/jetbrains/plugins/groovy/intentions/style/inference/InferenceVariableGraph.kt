// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

/**
 * @author knisht
 */

class InferenceVariableGraph(merges: List<List<InferenceVariable>>, private val session: GroovyInferenceSession) {

  private val representativeMap: MutableMap<InferenceVariable, InferenceVariable> = LinkedHashMap()
  val nodes: MutableMap<InferenceVariable, InferenceVariableNode> = LinkedHashMap()
  val variableInstantiations: MutableMap<InferenceVariable, PsiType> = LinkedHashMap()

  init {
    merges.flatten().forEach { variableInstantiations[it] = it.instantiation; it.instantiation = PsiType.NULL }
    for (merge in merges) {
      val representative = merge[0]
      nodes[representative] = InferenceVariableNode(representative)
      merge.forEach { representativeMap[it] = representative }
    }
    representativeMap.values.forEach {
      nodes[it]!!.collectDependencies(it, session, nodes)
    }
    val order = topologicalOrder()
    for (node in order) {
      for (dependentNode in node.subtypes) {
        dependentNode.directParent = node
        dependentNode.graphDepth = node.graphDepth + 1
      }
    }
    collapseEdges(order)
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


  private fun merge(anchorNode: InferenceVariableNode, mergingNode: InferenceVariableNode, commonNodes: Set<InferenceVariableNode>) {
    if (mergingNode.directParent in commonNodes || mergingNode.directParent == null) {
      mergingNode.directParent = anchorNode
    } else {
      merge(anchorNode, mergingNode.directParent!!, commonNodes)
    }
  }

  private fun collectParents(node: InferenceVariableNode?): MutableSet<InferenceVariableNode> {
    if (node == null) {
      return mutableSetOf()
    } else {
      val parents = collectParents(node.directParent)
      parents.add(node)
      return parents
    }
  }

  private fun mergeBranches(node: InferenceVariableNode) {
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


  private fun collapseEdges(order: List<InferenceVariableNode>) {
    var iterativeSubstitutor = PsiSubstitutor.EMPTY
    for (node in order) {
      if (node.supertypes.size >= 2) {
        mergeBranches(node)
      }
      if (node.supertypes.isEmpty() && node.supertypes.isEmpty()) {
        node.inferenceVariable.instantiation = node.inferenceVariable.getBounds(
          InferenceBound.UPPER).firstOrNull { it is PsiClassType && it.hasParameters() } ?: PsiType.NULL
        node.inferenceVariable.instantiation = iterativeSubstitutor.substitute(node.inferenceVariable.instantiation)
      }
      if (node.inferenceVariable.instantiation != PsiType.NULL) {
        iterativeSubstitutor = iterativeSubstitutor.put(node.inferenceVariable, node.inferenceVariable.instantiation)
      }
    }
  }

  /**
   * Allows to collapse redundant type parameter if it represents parameter of the [method]
   * Example: `def <T, U> void foo(List<T> ts, U u)` {}. Here U will be flexible type parameter and may be removed.
   */
  fun adjustFlexibleVariables(method: GrMethod) {
    for (parameter in method.parameters) {
      val variable = session.getInferenceVariable(session.substituteWithInferenceVariables(parameter.type))
      if (variable in representativeMap) {
        val node = nodes[getRepresentative(variable)]!!
        if ((node.subtypes + node.supertypes + node.weakSupertypes + node.weakSubtypes).isEmpty()) {
          node.inferenceVariable.instantiation = variableInstantiations[node.inferenceVariable]
        }
        else if (node.directParent != null) {
          node.inferenceVariable.instantiation = node.directParent?.inferenceVariable?.type() ?: PsiType.NULL
        }
      }
    }
  }

  fun getParent(variable: InferenceVariable): InferenceVariable? {
    return nodes[variable]?.directParent?.inferenceVariable
  }


  fun getRepresentative(variable: InferenceVariable): InferenceVariable? {
    return representativeMap[variable]
  }

}