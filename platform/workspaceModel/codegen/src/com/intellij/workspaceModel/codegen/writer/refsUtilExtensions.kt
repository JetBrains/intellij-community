package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.writer.allFields
import com.intellij.workspaceModel.codegen.writer.isOverride
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

val ObjClass<*>.refsFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isRefType() }

val ObjClass<*>.allRefsFields: List<OwnProperty<*, *>>
  get() = allFields.filter { it.valueType.isRefType() }

val ObjClass<*>.vfuFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isVfuType() }

fun ValueType<*>.getRefType(): ValueType.ObjRef<*> = when (this) {
  is ValueType.ObjRef<*> -> this
  is ValueType.Optional<*> -> type.getRefType()
  is ValueType.Collection<*, *> -> elementType.getRefType()
  else -> error("Unsupported type of requester, should be called only if `isRefType` is true")
}

fun ValueType<*>.isRefType(): Boolean = when (this) {
  is ValueType.ObjRef<*> -> true
  is ValueType.Optional<*> -> type.isRefType()
  is ValueType.Collection<*, *> -> elementType.isRefType()
  else -> false
}

fun ValueType<*>.isVfuType(): Boolean = when (this) {
  is ValueType.Blob -> javaClassName == VirtualFileUrl::class.java.name
  is ValueType.Optional<*> -> type.isVfuType()
  is ValueType.Collection<*, *> -> elementType.isVfuType()
  else -> false
}

fun sups(vararg extensions: String?): String = extensions.filterNotNull().joinToString(separator = ", ")
