// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * @author knisht
 */
fun produceTypeParameterName(index: Int): String {
  // todo: fix possible collisions
  val namerange = 'Z'.toByte() - 'T'.toByte()
  return ('T'.toByte() + index % namerange).toChar().toString() + (index / namerange).toString()
}

class NameGenerator {
  var counter = 0
  val name: String
    get() {
      val name = produceTypeParameterName(counter)
      ++counter
      return name
    }
}

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceVariable>

fun resolveInferenceVariableOrder(inferenceVars: Collection<InferenceVariable>,
                                  session: InferenceSession): List<List<InferenceVariableDependency>> {
  val nodes = LinkedHashMap<InferenceVariable, InferenceGraphNode>()
  val dependencies = LinkedHashMap<InferenceVariable, InferenceVariableDependency>()
  inferenceVars.forEach { nodes[it] = InferenceGraphNode(it); dependencies[it] = InferenceVariableDependency(it) }
  for (inferenceVar in inferenceVars) {
    val node = nodes[inferenceVar]!!
    for (dependency in inferenceVar.getDependencies(session)) {
      val dependencyNode = nodes[dependency] ?: continue
      if (dependency.type() in inferenceVar.getBounds(InferenceBound.UPPER) ||
          inferenceVar.type() in dependency.getBounds(InferenceBound.LOWER) ||
          inferenceVar.type() in dependency.getBounds(InferenceBound.EQ)) {
        node.addDependency(dependencyNode)
        dependencies[inferenceVar]!!.upper.add(dependency)
        dependencies[dependency]!!.lower.add(inferenceVar)
      }
      if (dependency.type() in inferenceVar.getBounds(InferenceBound.LOWER) ||
          inferenceVar.type() in dependency.getBounds(InferenceBound.UPPER) ||
          dependency.type() in inferenceVar.getBounds(InferenceBound.EQ)) {
        dependencyNode.addDependency(node)
        dependencies[inferenceVar]!!.lower.add(dependency)
        dependencies[dependency]!!.upper.add(inferenceVar)
      }
    }
  }
  return InferenceVariablesOrder.initNodes(nodes.values).map { it.value.map { dependencies[it]!! } }
}


fun isDependsOnInferenceVariable(variableDependency: InferenceVariableDependency): Boolean {
  return variableDependency.upper.isNotEmpty() || variableDependency.lower.isNotEmpty()
}

data class InferenceVariableDependency(val variable: InferenceVariable) {
  val upper: MutableList<InferenceVariable> = ArrayList()
  val lower: MutableList<InferenceVariable> = ArrayList()
}