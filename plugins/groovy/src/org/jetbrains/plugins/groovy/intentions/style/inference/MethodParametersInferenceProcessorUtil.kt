// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import java.util.HashSet
import kotlin.collections.Collection
import kotlin.collections.LinkedHashMap
import kotlin.collections.Set
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set


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

fun createInferenceVariableGraph(inferenceVars: Collection<InferenceVariable>,
                                 session: GroovyInferenceSession): InferenceVariableGraph {
  val nodes = LinkedHashMap<InferenceVariable, InferenceGraphNode>()
  inferenceVars.forEach { nodes[it] = InferenceGraphNode(it); }

  for (inferenceVar in inferenceVars) {
    val node = nodes[inferenceVar]!!
    for (dependency in inferenceVar.getDependencies(session)) {
      val dependencyNode = nodes[dependency] ?: continue
      if (dependency.type() in inferenceVar.getBounds(InferenceBound.EQ) || inferenceVar.type() in dependency.getBounds(
          InferenceBound.EQ)) {
        dependencyNode.addDependency(node)
        node.addDependency(dependencyNode)
      }
      if (dependency.type() in inferenceVar.getBounds(InferenceBound.UPPER) ||
          inferenceVar.type() in dependency.getBounds(InferenceBound.LOWER)) {
        node.addDependency(dependencyNode)
      }
      if (dependency.type() in inferenceVar.getBounds(InferenceBound.LOWER) ||
          inferenceVar.type() in dependency.getBounds(InferenceBound.UPPER)) {
        dependencyNode.addDependency(node)
      }
    }
  }
  return InferenceVariableGraph(InferenceVariablesOrder.initNodes(nodes.values).map { it.value }, session)
}


fun getInferenceVariable(session: GroovyInferenceSession, variableType: PsiType): InferenceVariable {
  return session.getInferenceVariable(session.substituteWithInferenceVariables(variableType))
}

fun getConstantInferenceVariables(constantTypeParameters: Array<PsiTypeParameter>,
                                  session: GroovyInferenceSession): Set<InferenceVariable> {
  val constantInferenceVariables = HashSet<InferenceVariable>()
  for (param in constantTypeParameters) {
    val constantInferenceVariable = getInferenceVariable(session, param.type())
    constantInferenceVariable.instantiation = param.type()
    constantInferenceVariables.add(constantInferenceVariable)
  }
  return constantInferenceVariables
}

fun collectRepresentativeSubstitutor(graph: InferenceVariableGraph): PsiSubstitutor {
  var representativeSubstitutor = PsiSubstitutor.EMPTY
  graph.nodes.keys.forEach {
    representativeSubstitutor = representativeSubstitutor.put(it, graph.getRepresentative(it)?.type());
    val upperType = graph.getParent(it)?.type()
    if (upperType != null) {
      representativeSubstitutor = representativeSubstitutor.put(it, upperType)
    }
  }
  return representativeSubstitutor
}
