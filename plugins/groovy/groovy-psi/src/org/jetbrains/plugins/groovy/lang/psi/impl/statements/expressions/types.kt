// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TypesKt")
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.psi.*

open class GrTypeMapper(val context: PsiElement): PsiTypeMapper() {

  override fun visitClassType(classType: PsiClassType): PsiType? {
    val result = classType.resolveGenerics()
    val element = result.element ?: return null

    val parameters = classType.parameters
    val replacedParams = parameters.map { arg -> arg?.accept(this) }.toTypedArray()
    return JavaPsiFacade.getElementFactory(context.project).createType(element, *replacedParams)
  }
}

fun promoteLowerBoundWildcard(type: PsiType, context: PsiElement): PsiType? {
  val visitor = object : GrTypeMapper(context) {
    override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType): PsiType {
      return if (capturedWildcardType.wildcard.isSuper) capturedWildcardType else capturedWildcardType.upperBound
    }
  }

  return type.accept(visitor)
}
