// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession

/**
 * @author knisht
 */

class InferenceVariableGraph(merges: List<List<InferenceVariable>>, session: GroovyInferenceSession) {

  private val representativeMap: MutableMap<InferenceVariable, InferenceVariable> = HashMap()
  val nodes: MutableMap<InferenceVariable, InferenceVariableNode> = HashMap()
  private val variableInstantiations: MutableMap<InferenceVariable, PsiType> = HashMap()

  init {
    merges.flatten().forEach { variableInstantiations[it] = it.instantiation; it.instantiation = PsiType.NULL }
    for (merge in merges) {
      val representative = merge[0]
      nodes[representative] = InferenceVariableNode(representative)
      merge.forEach {
        representativeMap[it] = representative; nodes[representative]!!.collectDependencies(it, session, nodes)
      }
    }
    val order = topologicalOrder()
    for (node in order) {
      for (dependentNodes in node.subtypes) {
        dependentNodes.directParent = node
      }
    }
    collapseEdges()
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
    // todo: weak dependencies
    for (dependentNode in node.subtypes) {
      if (dependentNode !in visited) {
        traverseGraph(dependentNode, visited, order)
      }
    }
    order.add(node)
  }


  private fun collapseEdges() {
    for (node in nodes.values) {
      if (node.subtypes.isEmpty() && node.supertypes.isEmpty() && node.weakDependencies.isEmpty()) {
        node.inferenceVariable.instantiation = variableInstantiations[node.inferenceVariable]
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