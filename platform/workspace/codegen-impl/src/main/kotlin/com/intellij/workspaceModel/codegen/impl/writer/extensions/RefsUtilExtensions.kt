package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.VirtualFileUrl
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType

internal val ObjClass<*>.refsFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isRefType() }

internal val ObjClass<*>.allRefsFields: List<OwnProperty<*, *>>
  get() = allFields.filter { it.valueType.isRefType() }

internal val ObjClass<*>.vfuFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isVfuType() }

internal val ObjClass<*>.allExtensions: List<ExtProperty<*, *>>
  get() = extensionRefs + ownExtensions

internal val ObjClass<*>.extensionRefs: List<ExtProperty<*, *>>
  get() = module.extensions.filter { it.receiver.module != module && it.valueType.isRefType() && it.valueType.getRefType().target == this }

internal val ObjClass<*>.ownExtensions: List<ExtProperty<*, *>>
  get() = module.extensions.filter { it.receiver == this }

internal fun ValueType<*>.getRefType(): ValueType.ObjRef<*> = when (this) {
  is ValueType.ObjRef<*> -> this
  is ValueType.Optional<*> -> type.getRefType()
  is ValueType.Collection<*, *> -> elementType.getRefType()
  else -> error("${this.javaType} isn't a reference type. The method has to be called only for the reference type of fields")
}

internal fun ValueType<*>.isRefType(): Boolean = when (this) {
  is ValueType.ObjRef<*> -> true
  is ValueType.Optional<*> -> type.isRefType()
  is ValueType.Collection<*, *> -> elementType.isRefType()
  else -> false
}

internal fun ValueType<*>.isVfuType(): Boolean = when (this) {
  is ValueType.Blob -> kotlinClassName == VirtualFileUrl.decoded
  is ValueType.Optional<*> -> type.isVfuType()
  is ValueType.Collection<*, *> -> elementType.isVfuType()
  else -> false
}

internal fun sups(vararg extensions: String?): String = extensions.filterNotNull().joinToString(separator = ", ")
