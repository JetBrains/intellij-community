// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.types.JvmPrimitiveType
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind.BOOLEAN
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind.VOID
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.lang.jvm.types.JvmType
import java.beans.Introspector

fun getPropertyNameAndKind(method: JvmMethod): Pair<String, PropertyKind>? {
  if (method.isConstructor) return null
  val (name, kind) = getPropertyNameAndKind(method.name) ?: return null
  return if (method.checkKind(kind)) name to kind else null
}

fun getPropertyNameAndKind(accessorName: String): Pair<String, PropertyKind>? {
  val propertyKind = getKindByPrefix(accessorName) ?: return null
  val prefixLength = propertyKind.prefix.length
  if (!checkBaseName(accessorName, prefixLength)) return null
  val propertyName = Introspector.decapitalize(accessorName.substring(prefixLength))
  return propertyName to propertyKind
}

private fun getKindByPrefix(accessorName: String): PropertyKind? = PropertyKind.values().find { accessorName.startsWith(it.prefix) }

private fun checkBaseName(accessorName: String, prefixLength: Int): Boolean {
  if (accessorName.length <= prefixLength) return false
  if (accessorName[prefixLength].isUpperCase()) return true // getX.*
  if (accessorName.length <= prefixLength + 1) return false
  return accessorName[prefixLength + 1].isUpperCase()       // getxX.*
}

/**
 * This method doesn't check if method name is an accessor name
 */
internal fun JvmMethod.checkKind(kind: PropertyKind): Boolean {
  return when (kind) {
    GETTER -> parameters.isEmpty() && !returnType.isPrimitive(VOID)
    BOOLEAN_GETTER -> parameters.isEmpty() && returnType.isPrimitiveOrBoxed(BOOLEAN)
    SETTER -> parameters.size == 1
  }
}

private fun JvmType?.isPrimitiveOrBoxed(kind: JvmPrimitiveTypeKind) = isPrimitive(kind) || isBoxed(kind)
private fun JvmType?.isPrimitive(kind: JvmPrimitiveTypeKind) = this is JvmPrimitiveType && this.kind == kind
private fun JvmType?.isBoxed(kind: JvmPrimitiveTypeKind) = resolveToClass()?.qualifiedName == kind.boxedFqn
private fun JvmType?.resolveToClass() = (this as? JvmReferenceType)?.resolve() as? JvmClass

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
