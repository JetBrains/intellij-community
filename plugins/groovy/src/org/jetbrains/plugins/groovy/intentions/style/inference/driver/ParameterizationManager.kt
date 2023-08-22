// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiIntersectionType.createIntersection
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


private class Parameterizer(val context: PsiElement,
                            val registerTypeParameterAction: (PsiType?) -> PsiClassType) : PsiTypeVisitor<PsiType>() {
  val elementFactory = GroovyPsiElementFactory.getInstance(context.project)


  override fun visitClassType(classType: PsiClassType): PsiClassType? {
    val generifiedClassType = if (classType.isRaw) {
      val resolveResult = classType.resolve()!!
      val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(context.manager) }
      elementFactory.createType(resolveResult, *wildcards)
    }
    else {
      classType
    }
    val mappedParameters = generifiedClassType.parameters.map { it.accept(this) }.toTypedArray()
    if (classType.equalsToText(JAVA_LANG_OBJECT)) {
      return registerTypeParameterAction(null)
    }
    else {
      // we should not create new type parameter here because it may be a component of an intersection type
      return elementFactory.createType(classType.resolve() ?: return null, *mappedParameters)
    }
  }

  override fun visitWildcardType(wildcardType: PsiWildcardType): PsiType? {
    val upperBound = if (wildcardType.isExtends && !wildcardType.extendsBound.equalsToText(JAVA_LANG_OBJECT)) {
      val bound = wildcardType.extendsBound
      if (bound is PsiCapturedWildcardType) {
        return bound.accept(this)
      }
      bound.accept(this)
    }
    else {
      null
    }
    return registerTypeParameterAction(upperBound)
  }

  override fun visitIntersectionType(intersectionType: PsiIntersectionType): PsiType {
    val parametrizedConjuncts = intersectionType.conjuncts.mapNotNull { it.accept(this) }
    return createIntersection(parametrizedConjuncts)
  }
}


data class ParameterizationResult(val type: PsiType, val typeParameters: List<PsiTypeParameter>)

class ParameterizationManager(method: GrMethod) {
  val nameGenerator = NameGenerator("_ENRICH" + method.hashCode(), context = method)
  private val elementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val context: PsiElement = method

  private fun registerTypeParameter(supertype: PsiType?, storage: MutableCollection<PsiTypeParameter>): PsiClassType {
    val typeParameter =
      elementFactory.createProperTypeParameter(nameGenerator.name, supertype)
    storage.add(typeParameter)
    return typeParameter.type()
  }

  /**
   * Creates type parameter with upper bound of [target].
   * If [target] is has type arguments, they will be parametrized.
   */
  fun createDeeplyParameterizedType(target: PsiType): ParameterizationResult {
    val createdTypeParameters = mutableListOf<PsiTypeParameter>()
    val registerAction =
      { upperBound: PsiType? -> registerTypeParameter(upperBound, createdTypeParameters) }
    val visitor = Parameterizer(context, registerAction)
    val calculatedType =
      when {
        target is PsiArrayType ->
          if (target.componentType is PsiPrimitiveType) target else registerAction(target.componentType.accept(visitor)).createArrayType()
        target.isTypeParameter() -> registerAction(target)
        target is PsiIntersectionType -> registerAction(target.accept(visitor))
        target == getJavaLangObject(context) -> registerAction(null)
        else -> registerAction(target.accept(visitor))
      }
    return ParameterizationResult(calculatedType, createdTypeParameters)
  }
}
