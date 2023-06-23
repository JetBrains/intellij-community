package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType

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
  else -> error("${this.javaType} isn't a reference type. The method has to be called only for the reference type of fields")
}

fun ValueType<*>.isRefType(): Boolean = when (this) {
  is ValueType.ObjRef<*> -> true
  is ValueType.Optional<*> -> type.isRefType()
  is ValueType.Collection<*, *> -> elementType.isRefType()
  else -> false
}

fun ValueType<*>.isVfuType(): Boolean = when (this) {
  is ValueType.Blob -> javaClassName == VirtualFileUrl.decoded
  is ValueType.Optional<*> -> type.isVfuType()
  is ValueType.Collection<*, *> -> elementType.isVfuType()
  else -> false
}

fun sups(vararg extensions: String?): String = extensions.filterNotNull().joinToString(separator = ", ")
