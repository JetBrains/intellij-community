// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.intentions.style.inference.graph.InferenceUnitNode
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession


class NameGenerator(private val restrictions: Collection<String> = emptySet()) {
  companion object {
    private const val nameRange = 'Z'.toByte() - 'T'.toByte()

    private fun produceTypeParameterName(index: Int): String {
      return ('T'.toByte() + index % nameRange).toChar().toString() + (index / nameRange).toString()
    }
  }

  private var counter = 0

  val name: String
    get() {
      while (true) {
        val name = produceTypeParameterName(counter)
        ++counter
        if (name !in restrictions) {
          return name
        }
      }
    }

}

typealias InferenceGraphNode = InferenceVariablesOrder.InferenceGraphNode<InferenceUnitNode>

fun getInferenceVariable(session: GroovyInferenceSession, variableType: PsiType): InferenceVariable {
  return session.getInferenceVariable(session.substituteWithInferenceVariables(variableType))
}

fun Iterable<PsiType>.flattenIntersections(): Iterable<PsiType> {
  return this.flatMap { if (it is PsiIntersectionType) it.conjuncts.asIterable() else listOf(it) }
}

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

fun PsiType.ensureWildcards(factory: GroovyPsiElementFactory, manager: PsiManager): PsiType {
  val enclosing = this
  return accept(object : PsiTypeMapper() {
    override fun visitClassType(classType: PsiClassType?): PsiType? {
      classType ?: return classType
      val mappedParameters = classType.parameters.map { it.accept(this) }
      val newType = factory.createType(classType.resolve()!!, *mappedParameters.toTypedArray())
      if (classType == enclosing) {
        return newType
      }
      else {
        return PsiWildcardType.createExtends(manager, newType)
      }
    }

  })
}

fun isClosureType(type: PsiType?): Boolean {
  return (type as? PsiClassType)?.rawType()?.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE) ?: false
}

fun PsiSubstitutor.recursiveSubstitute(type: PsiType): PsiType {
  val substituted = substitute(type)
  return if (substituted == type) {
    type
  }
  else {
    recursiveSubstitute(substituted)
  }
}

class UnreachableException : RuntimeException("This statement is unreachable")

fun unreachable(): Nothing {
  throw UnreachableException()
}

fun <T, U> cartesianProduct(leftRange: Iterable<T>, rightRange: Iterable<U>): List<Pair<T, U>> =
  leftRange.flatMap { left -> rightRange.map { right -> Pair(left, right) } }