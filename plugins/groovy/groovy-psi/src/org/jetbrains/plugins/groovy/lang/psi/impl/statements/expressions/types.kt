// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TypesKt")
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.psi.*

open class GrTypeMapper(val context: PsiElement): PsiTypeMapper() {
  private val visitedClassTypes = mutableSetOf<PsiClassType>()

  override fun visitClassType(classType: PsiClassType): PsiType? {
    val result = classType.resolveGenerics()
    val element = result.element ?: return null
    visitedClassTypes.add(classType)

    val parameters = classType.parameters
    val replacedParams = parameters.map { arg ->
      if (arg in visitedClassTypes) {
        PsiWildcardType.createUnbounded(element.manager)
      }
      else {
        arg?.accept(this)
      }
    }.toTypedArray()
    return JavaPsiFacade.getElementFactory(context.project).createType(element, *replacedParams)
  }
}

fun promoteLowerBoundWildcard(type: PsiType, context: PsiElement): PsiType? {
  val visitor = object : GrTypeMapper(context) {
    override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType): PsiType? {
      return if (capturedWildcardType.wildcard.isSuper) capturedWildcardType else capturedWildcardType.upperBound
    }
  }

  return type.accept(visitor)
}
