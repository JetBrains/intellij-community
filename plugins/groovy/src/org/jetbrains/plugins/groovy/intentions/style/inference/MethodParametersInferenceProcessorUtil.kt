// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import java.util.HashSet
import kotlin.collections.LinkedHashMap
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

fun catchDependency(types: List<PsiType>, targetType: PsiType): Boolean {
  return types.any { it == targetType || (if (it is PsiIntersectionType) catchDependency(it.conjuncts.toList(), targetType) else false) }
}

fun determineDependencyRelation(left: InferenceVariable, right: InferenceVariable): InferenceBound? {
  if (catchDependency(left.getBounds(InferenceBound.EQ), right.type()) ||
      catchDependency(right.getBounds(InferenceBound.EQ), left.type())) {
    return InferenceBound.EQ
  }
  if (catchDependency(left.getBounds(InferenceBound.UPPER), right.type()) ||
      catchDependency(right.getBounds(InferenceBound.LOWER), left.type())) {
    return InferenceBound.UPPER
  }
  if (catchDependency(left.getBounds(InferenceBound.LOWER), right.type()) ||
      catchDependency(right.getBounds(InferenceBound.UPPER), left.type())) {
    return InferenceBound.LOWER
  }
  return null
}

fun getDependencies(type: PsiType, session: GroovyInferenceSession): Iterable<InferenceVariable> {
  return when {
    type is PsiIntersectionType -> type.conjuncts.flatMap { getDependencies(it, session) }
    session.getInferenceVariable(type) != null -> listOf(session.getInferenceVariable(type))
    else -> emptyList()
  }
}

fun InferenceVariable.getFullDependencies(session: GroovyInferenceSession): List<InferenceVariable> {
  val combined = getBounds(InferenceBound.LOWER) + this.getBounds(InferenceBound.UPPER) + this.getBounds(InferenceBound.EQ)
  return combined.flatMap { getDependencies(it, session) }
}

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceVariable>

fun createInferenceVariableGraph(inferenceVars: Collection<InferenceVariable>,
                                 session: GroovyInferenceSession): InferenceVariableGraph {
  val nodes = LinkedHashMap<InferenceVariable, InferenceGraphNode>()
  inferenceVars.forEach { nodes[it] = InferenceGraphNode(it); }

  for (inferenceVar in inferenceVars) {
    val node = nodes[inferenceVar]!!
    for (dependency in inferenceVar.getFullDependencies(session)) {
      val dependentNode = nodes[dependency] ?: continue
      val relation = determineDependencyRelation(inferenceVar, dependency) ?: continue
      when (relation) {
        InferenceBound.UPPER -> node.addDependency(dependentNode)
        InferenceBound.LOWER -> dependentNode.addDependency(node)
        InferenceBound.EQ -> {
          dependentNode.addDependency(node); node.addDependency(dependentNode);
        }
      }
    }
  }
  val order = InferenceVariablesOrder.initNodes(nodes.values).map { it.value }
  return InferenceVariableGraph(order, session)
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
  graph.initialVariableInstantiations.keys.forEach {
    representativeSubstitutor = representativeSubstitutor.put(it, graph.getRepresentative(it)?.type())
  }
  return representativeSubstitutor
}

fun InferenceVariable.upperBounds() = getBounds(InferenceBound.UPPER)

fun GroovyPsiElementFactory.createProperTypeParameter(name: String, superTypes: Array<out PsiClassType>): PsiTypeParameter {
  val builder = StringBuilder()
  builder.append("public <").append(name)
  if (superTypes.size > 1 || superTypes.size == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
    builder.append(" extends ")
    for (type in superTypes) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) continue
      builder.append(type.getCanonicalText(true)).append('&')
    }

    builder.delete(builder.length - 1, builder.length)
  }
  builder.append("> void foo(){}")
  try {
    return createMethodFromText(builder.toString(), null).typeParameters[0]
  }
  catch (e: RuntimeException) {
    throw IncorrectOperationException("type parameter text: $builder")
  }

}