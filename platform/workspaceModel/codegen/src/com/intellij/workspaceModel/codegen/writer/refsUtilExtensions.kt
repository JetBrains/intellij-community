package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.writer.allFields
import com.intellij.workspaceModel.codegen.writer.isOverride
import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.deft.ValueType as OldValueType

val TStructure<*, *>.refsFields: List<Field<out Obj, Any?>>
  get() = newFields.filter { it.name != "parent" && it.type.isRefType() }
val ObjClass<*>.refsFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.name != "parent" && it.valueType.isRefType() }

val ObjClass<*>.allRefsFields: List<OwnProperty<*, *>>
  get() = allFields.filter { it.name != "parent" && it.valueType.isRefType() }

val ObjClass<*>.vfuFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isVfuType() }

fun OldValueType<*>.getRefType(): TRef<*> = when (this) {
  is TRef<*> -> this
  is TOptional<*> -> type.getRefType()
  is TCollection<*, *> -> elementType.getRefType()
  else -> error("Unsupported type of requester, should be called only if `isRefType` is true")
}

fun ValueType<*>.getRefType(): ValueType.ObjRef<*> = when (this) {
  is ValueType.ObjRef<*> -> this
  is ValueType.Optional<*> -> type.getRefType()
  is ValueType.Collection<*, *> -> elementType.getRefType()
  else -> error("Unsupported type of requester, should be called only if `isRefType` is true")
}

fun OldValueType<*>.isRefType(): Boolean = when (this) {
  is TRef<*> -> true
  is TOptional<*> -> type.isRefType()
  is TCollection<*, *> -> elementType.isRefType()
  else -> false
}

fun ValueType<*>.isRefType(): Boolean = when (this) {
  is ValueType.ObjRef<*> -> true
  is ValueType.Optional<*> -> type.isRefType()
  is ValueType.Collection<*, *> -> elementType.isRefType()
  else -> false
}

fun ValueType<*>.isVfuType(): Boolean = when (this) {
  is ValueType.Blob -> javaClassName == "VirtualFileUrl"
  is ValueType.Optional<*> -> type.isVfuType()
  is ValueType.Collection<*, *> -> elementType.isVfuType()
  else -> false
}

fun sups(vararg extensions: String?): String = extensions.filterNotNull().joinToString(separator = ", ")
