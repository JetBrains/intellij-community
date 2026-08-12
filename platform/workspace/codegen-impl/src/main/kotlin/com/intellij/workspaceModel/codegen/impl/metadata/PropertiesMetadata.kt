package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.TypeProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.metadata.model.getCustomTypeConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getEntityReferenceConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getExtPropertyMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getKnownClassConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getOwnPropertyMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getParameterizedTypeConstructor
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.refsConnectionType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isComputable
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isEntityRef
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.withDefault
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdFieldName

fun MetadataContext.buildPropertyMetadata(obj: TypeProperty<*>): String? {
  if (obj is ObjProperty<*,*> && obj.isComputable && obj.name != symbolicIdFieldName) return null
  return when (obj) {
    is OwnProperty<*, *> -> {
      buildOwnPropertyMetadata(obj)
    }
    is ExtProperty<*, *> -> {
      if (obj.valueType.isEntityRef(obj))
        buildExtPropertyMetadata(obj)
      else
        buildOwnPropertyMetadata(obj)
    }
    is ValueType.ClassProperty -> {
      buildClassPropertyMetadata(obj)
    }
    else -> {
      reportError("Unknown property type for ${obj.name}: ${obj.javaClass.name}")
      ""
    }
  }
}

private fun MetadataContext.buildOwnPropertyMetadata(objProperty: ObjProperty<*, *>): String {
  return getOwnPropertyMetadataConstructor(
    name = objProperty.name.withDoubleQuotes(),
    valueType = buildValueTypeMetadata(objProperty),
    isComputable = objProperty.isComputable,
    isOpen = objProperty.open,
    withDefault = objProperty.withDefault,
    isKey = if (objProperty is OwnProperty) objProperty.isKey else false
  )
}

private fun MetadataContext.buildValueTypeMetadata(property: TypeProperty<*>): String {
  val valueType = property.valueType
  if (valueType.isEntityRef(property)) { // `property is ObjProperty<*,*>` check inside
    property as ObjProperty<*, *>
    val unwrapped = unwrapReferenceType(valueType)
    if (unwrapped == null) {
      reportPropertyError("$valueType isn't a reference type, but was expected to be while collecting metadata", property)
      return ""
    }
    return buildReferenceValueType(property, unwrapped)
  }
  return buildNonReferenceValueType(valueType)
}

private fun GeneratorContext.buildReferenceValueType(property: ObjProperty<*, *>, valueType: ValueType.ObjRef<*>): String {
  return getEntityReferenceConstructor(
    entityFqName = getJavaFullName(valueType.target.name, valueType.target.module.name),
    isChild = valueType.child,
    connectionType = refsConnectionType(property, valueType),
    isNullable = property.valueType is ValueType.Optional<*>
  )
}

private fun MetadataContext.buildNonReferenceValueType(valueType: ValueType<*>, isNullable: Boolean = false): String {
  return when (valueType) {
    is ValueType.Optional<*> -> buildNonReferenceValueType(valueType.type, true)
    is ValueType.Primitive<*>, is ValueType.Nothing, is ValueType.Any -> buildPrimitiveValueType(valueType, isNullable)
    is ValueType.Collection<*, *> -> buildParametrizedValueType(valueType, isNullable, buildNonReferenceValueType(valueType.elementType))
    // TODO: only one valueType passed to buildNonReferenceValueType for map. check
    is ValueType.Map<*, *> -> buildParametrizedValueType(valueType,
                                                         isNullable,
                                                         buildNonReferenceValueType(valueType.keyType),
                                                         buildNonReferenceValueType(valueType.valueType))
    is ValueType.JvmClass<*> -> buildJvmClassValueType(valueType, isNullable)
    is ValueType.ObjRef<*> -> buildKnownReferenceMetadata(valueType, isNullable)
    else -> {
      reportError("$valueType type isn't supported")
      ""
    }
  }
}

private fun MetadataContext.buildParametrizedValueType(
  valueType: ValueType<*>,
  isNullable: Boolean,
  vararg generics: String,
): String {
  // TODO("Test this")
  return getParameterizedTypeConstructor(primitive = buildPrimitiveValueType(valueType, isNullable), generics = generics.asList())
}

private fun GeneratorContext.buildObjRefValueType(valueTypeObjRef: ValueType.ObjRef<*>, isNullable: Boolean): String {
  return getCustomTypeConstructor(isNullable, getKnownClassConstructor(getFullName(valueTypeObjRef.target)))
}

private fun MetadataContext.buildExtPropertyMetadata(extProperty: ExtProperty<*, *>): String {
  return getExtPropertyMetadataConstructor(
    name = extProperty.name.withDoubleQuotes(),
    receiverFqn = getFullName(extProperty.receiver),
    valueType = buildValueTypeMetadata(extProperty),
    isComputable = extProperty.isComputable,
    isOpen = extProperty.open,
    withDefault = extProperty.withDefault
  )
}

private fun MetadataContext.buildClassPropertyMetadata(valueTypeClassProperty: ValueType.ClassProperty<*>): String {
  return getOwnPropertyMetadataConstructor(
    name = valueTypeClassProperty.name.withDoubleQuotes(),
    valueType = buildValueTypeMetadata(valueTypeClassProperty),
    isComputable = false,
    isOpen = false,
    withDefault = false,
    isKey = false
  )
}

private fun MetadataContext.buildKnownReferenceMetadata(valueTypeObjRef: ValueType.ObjRef<*>, isNullable: Boolean): String =
  getCustomTypeConstructor(isNullable, getKnownClassConstructor(getFullName(valueTypeObjRef.target)))