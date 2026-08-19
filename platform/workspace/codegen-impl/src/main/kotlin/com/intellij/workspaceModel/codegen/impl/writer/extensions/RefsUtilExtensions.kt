package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.writer.VirtualFileUrl
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties

internal val ObjClass<*>.refsFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isReferenceType() }

internal val ObjClass<*>.vfuFields: List<OwnProperty<*, *>>
  get() = fields.filter { !it.isOverride && it.valueType.isVfuType() }

internal fun GeneratorContext.getAllExtensions(objClass: ObjClass<*>): List<ExtProperty<*, *>> {
  return getExtensionRefs(objClass) + objClass.ownExtensions
}

internal fun GeneratorContext.getExtensionRefs(objClass: ObjClass<*>): List<ExtProperty<*, *>> {
  return objClass.module.extensions.filter { extProperty ->
    val unwrapped = unwrapReferenceType(extProperty.valueType)
    extProperty.receiver.module != objClass.module && unwrapped != null && unwrapped.target == objClass
  }
}

internal val ObjClass<*>.ownExtensions: List<ExtProperty<*, *>>
  get() = module.extensions.filter { it.receiver == this }

internal fun unwrapValueType(valueType: ValueType<*>): ValueType<*> {
  return when (valueType) {
    is ValueType.Optional<*> -> unwrapValueType(valueType.type)
    is ValueType.Collection<*, *> -> unwrapValueType(valueType.elementType)
    else -> valueType
  }
}

// TODO: too many usages
internal fun unwrapReferenceType(valueType: ValueType<*>): ValueType.ObjRef<*>? {
  val unwrapped = unwrapValueType(valueType)
  if (unwrapped is ValueType.ObjRef<*>)
    return unwrapped
  return null
}

internal fun ValueType<*>.isReferenceType(): Boolean {
  val unwrapped = unwrapReferenceType(this)
  return unwrapped != null
}

internal fun ValueType<*>.isVfuType(): Boolean {
  val unwrapped = unwrapValueType(this)
  return unwrapped is ValueType.Blob && unwrapped.kotlinClassName == VirtualFileUrl.decoded
}
