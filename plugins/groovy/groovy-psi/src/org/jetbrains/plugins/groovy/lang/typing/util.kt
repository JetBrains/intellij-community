// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TypeUtils")

package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.checkKind
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty

/**
 * @return boxed type if [this] is a primitive type, otherwise [this] type
 */
fun PsiType.box(context: GroovyPsiElement): PsiType? {
  if (this !is PsiPrimitiveType || this == PsiType.NULL) return this
  val typeName = boxedTypeName ?: error("This type is not NULL and still doesn't have boxed type name")
  return TypesUtil.createType(typeName, context)
}

fun getReadPropertyType(result: GroovyResolveResult): PsiType? {
  val baseType = getReadPropertyBaseType(result) ?: return null
  return result.substitutor.substitute(baseType)
}

private fun getReadPropertyBaseType(result: GroovyResolveResult): PsiType? {
  val resolved = result.element ?: return null
  if (resolved is GroovyProperty) {
    return resolved.propertyType
  }
  else if (resolved is PsiVariable) {
    return resolved.type
  }
  else if (resolved is PsiMethod) {
    if (resolved.checkKind(PropertyKind.GETTER) || resolved.checkKind(PropertyKind.BOOLEAN_GETTER)) {
      return resolved.returnType
    }
  }
  return null
}

fun getWritePropertyType(result: GroovyResolveResult): PsiType? {
  val baseType = getWritePropertyBaseType(result) ?: return null
  return result.substitutor.substitute(baseType)
}

private fun getWritePropertyBaseType(result: GroovyResolveResult): PsiType? {
  val resolved = result.element ?: return null
  if (resolved is GroovyProperty) {
    return resolved.propertyType
  }
  else if (resolved is PsiVariable) {
    return resolved.type
  }
  else if (resolved is PsiMethod && resolved.checkKind(PropertyKind.SETTER)) {
    return resolved.parameterList.parameters[0].type
  }
  else {
    return null
  }
}
