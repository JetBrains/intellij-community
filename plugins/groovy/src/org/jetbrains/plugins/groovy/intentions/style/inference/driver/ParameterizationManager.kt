// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


private class Parameterizer(val context: PsiElement,
                            val registerTypeParameterAction: (Iterable<PsiClassType?>) -> PsiClassType) : PsiTypeVisitor<PsiClassType>() {
  val elementFactory = GroovyPsiElementFactory.getInstance(context.project)


  override fun visitClassType(classType: PsiClassType?): PsiClassType? {
    classType ?: return classType
    val generifiedClassType = if (classType.isRaw) {
      val resolveResult = classType.resolve()!!
      val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(context.manager) }
      elementFactory.createType(resolveResult, *wildcards)
    }
    else {
      classType
    }
    val mappedParameters = generifiedClassType.parameters.map { it.accept(this) }.toTypedArray()
    if (classType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return registerTypeParameterAction(emptyList())
    }
    else {
      // we should not create new type parameter here because it may be a component of an intersection type
      return elementFactory.createType(classType.resolve() ?: return null, *mappedParameters)
    }
  }

  override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiClassType? {
    wildcardType ?: return null
    val upperBounds = if (wildcardType.isExtends) {
      listOf(wildcardType.extendsBound.accept(this))
    }
    else {
      emptyList()
    }
    return registerTypeParameterAction(upperBounds)
  }

  override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiClassType? {
    intersectionType ?: return null
    val parametrizedConjuncts = intersectionType.conjuncts.map { it.accept(this) }
    return registerTypeParameterAction(parametrizedConjuncts)
  }
}


data class ParameterizationResult(val type: PsiType, val typeParameters: List<PsiTypeParameter>)

class ParameterizationManager(method: GrMethod) {
  val nameGenerator = NameGenerator(method.typeParameters.map { it.name!! })
  private val elementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val context: PsiElement = method

  companion object {
    fun nonTrivial(type: PsiType) = !type.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)
  }

  private fun registerTypeParameter(supertypes: Iterable<PsiClassType?>, storage: MutableCollection<PsiTypeParameter>): PsiClassType {
    val typeParameter =
      elementFactory.createProperTypeParameter(nameGenerator.name, supertypes.filterNotNull().filter { nonTrivial(it) }.toTypedArray())
    storage.add(typeParameter)
    return typeParameter.type()
  }

  /**
   * Creates type parameter with upper bound of [target].
   * If [target] is parametrized, all it's parameter types will also be parametrized.
   */
  fun createDeeplyParameterizedType(target: PsiType, strict: Boolean = false): ParameterizationResult {
    val createdTypeParameters = mutableListOf<PsiTypeParameter>()
    val registerAction =
      { upperBounds: Iterable<PsiClassType?> -> registerTypeParameter(upperBounds, createdTypeParameters) }
    val visitor = Parameterizer(context, registerAction)
    val calculatedType =
      when {
        strict -> target.accept(visitor)
        target is PsiArrayType -> {
          if (target.componentType is PsiPrimitiveType) {
            target
          }
          else {
            target.componentType.accept(visitor).createArrayType()
          }
        }
        target.isTypeParameter() -> registerAction(listOf(target as PsiClassType))
        target is PsiIntersectionType -> target.accept(visitor)
        target == PsiType.getJavaLangObject(context.manager, context.resolveScope) -> registerAction(emptyList())
        else -> registerAction(listOf(target.accept(visitor)))
      }
    return ParameterizationResult(calculatedType, createdTypeParameters)
  }
}
