// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TypeUtils")

package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

/**
 * @return boxed type if [this] is a primitive type, otherwise [this] type
 */
fun PsiType.box(context: GroovyPsiElement): PsiType? {
  if (this !is PsiPrimitiveType || this == PsiType.NULL) return this
  val typeName = boxedTypeName ?: error("This type is not NULL and still doesn't have boxed type name")
  return TypesUtil.createType(typeName, context)
}
