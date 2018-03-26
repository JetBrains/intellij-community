// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType.getUnboxedType
import com.intellij.psi.PsiType
import java.beans.Introspector

fun getKindByAccessorName(accessorName: String): PropertyKind? {
  val propertyKind = getKindInternal(accessorName) ?: return null
  val prefixLength = propertyKind.prefix.length
  if (accessorName[prefixLength].isUpperCase()) return propertyKind
  //groovy support getyYyy as accessor
  if (accessorName.length > prefixLength + 1 && accessorName[prefixLength + 1].isUpperCase()) return propertyKind
  return null
}

fun getNameAndKind(accessorName: String): Pair<PropertyKind, String>? {
  val propertyKind = getKindByAccessorName(accessorName) ?: return null
  val propertyName = Introspector.decapitalize(accessorName.substring(propertyKind.prefix.length))
  return propertyKind to propertyName
}

private fun getKindInternal(accessorName: String) : PropertyKind? {
  for (kind in PropertyKind.values()) {
    val prefix = kind.prefix
    val prefixLength = prefix.length
    if (accessorName.startsWith(prefix) && accessorName.length > prefixLength) return kind
  }
  return null
}

/**
 * This method doesn't check if method name is an accessor name
 */
internal fun PsiMethod.checkKind(kind: PropertyKind): Boolean {
  val expectedParamCount = if (kind === SETTER) 1 else 0
  if (parameterList.parametersCount != expectedParamCount) return false

  if (kind == GETTER && returnType == PsiType.VOID) return false
  if (kind == BOOLEAN_GETTER && !returnType.isBooleanOrBoxed()) return false

  return true
}

private fun PsiType?.isBooleanOrBoxed(): Boolean {
  return this == PsiType.BOOLEAN || getUnboxedType(this) == PsiType.BOOLEAN
}

internal fun String.isPropertyName(): Boolean {
  return GroovyPropertyUtils.isPropertyName(this)
}

/**
 * @return accessor name by [propertyName] assuming it is valid
 */
fun PropertyKind.getAccessorName(propertyName: String): String {
  val suffix = if (propertyName.length > 1 && propertyName[1].isUpperCase()) {
    propertyName
  }
  else {
    propertyName.capitalize()
  }
  return prefix + suffix
}
