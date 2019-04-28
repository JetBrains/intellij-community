// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession


/**
 * @author knisht
 */
fun produceTypeParameterName(index: Int): String {
  // todo: fix possible collisions
  val nameRange = 'Z'.toByte() - 'T'.toByte()
  return ('T'.toByte() + index % nameRange).toChar().toString() + (index / nameRange).toString()
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

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceUnit>

fun getInferenceVariable(session: GroovyInferenceSession, variableType: PsiType): InferenceVariable {
  return session.getInferenceVariable(session.substituteWithInferenceVariables(variableType))
}

fun collectRepresentativeSubstitutor(graph: InferenceUnitGraph,
                                     registry: InferenceUnitRegistry): PsiSubstitutor {
  var representativeSubstitutor = PsiSubstitutor.EMPTY
  registry.getUnits().forEach {
    representativeSubstitutor = representativeSubstitutor.put(it.initialTypeParameter,
                                                              graph.getRepresentative(it).type)
  }
  return representativeSubstitutor
}

fun Iterable<PsiType>.flattenIntersections(): Iterable<PsiType> {
  return this.flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else listOf(it) }
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