package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.impl.metadata.model.*
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.classes.javaDataName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionType

internal fun interface MetadataBuilder<T> {
  fun buildMetadata(obj: T): String
}

internal data class BuiltPrimitiveType(private val type: String, private val isNullable: Boolean) {
  fun getVariableName(): String = "primitiveType$type${if (isNullable) "" else "Not"}Nullable"

  fun getConstructor(): String = getPrimitiveTypeConstructor(isNullable, type.escapeDollar().withDoubleQuotes())
}

/**
 * Collects metadata for [WorkspaceEntity] and returns it as string of [EntityMetadataEntity] instance.
 *
 * Metadata stores information about:
 * * name of the entity
 * * [WorkspaceEntityData] name of the entity
 * * entity properties metadata
 * * extension properties that refer to this entity
 * * supertypes as list of strings
 * * is entity abstract or not
 */
internal class EntityMetadataBuilder(private val builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): MetadataBuilder<ObjClass<*>> {
  override fun buildMetadata(obj: ObjClass<*>): String {
    val propertyBuilder = PropertyMetadataBuilder(builtPrimitiveTypes)
    return obj.buildEntity(propertyBuilder)
  }
}

/**
 * Collects metadata for [ObjProperty] and [ValueType.ClassProperty] and returns it as string of [PropertyMetadata] instance.
 *
 * Metadata stores information about:
 * * name of the property
 * * value type metadata of the property
 * * is property computable
 * * is property open
 * * property has default value or not
 *
 * @property [classBuilder] is used to build custom classes, if value type is [ValueType.JvmClass]
 */
internal class PropertyMetadataBuilder(private val builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): MetadataBuilder<TypeProperty<*>> {
  val classBuilder: MetadataBuilder<ValueType.JvmClass<*>> = ClassMetadataBuilder(this)

  override fun buildMetadata(obj: TypeProperty<*>): String {
    val valueTypeBuilder = createValueTypeBuilder(obj)
    return when (obj) {
      is OwnProperty<*, *> -> obj.buildOwnProperty(valueTypeBuilder)
      is ExtProperty<*, *> ->
        if (obj.valueType.isEntityRef(obj)) obj.buildExtProperty(valueTypeBuilder) else obj.buildOwnProperty(valueTypeBuilder)
      is ValueType.ClassProperty -> obj.buildClassProperty(valueTypeBuilder)
      else -> unsupportedType(obj)
    }
  }

  private fun createValueTypeBuilder(property: TypeProperty<*>): MetadataBuilder<ValueType<*>> =
    ValueTypeMetadataBuilder(property, classBuilder, builtPrimitiveTypes)
}

/**
 * Collects metadata for [ValueType] and returns it as string of [ValueTypeMetadata] instance.
 *
 * Supported value types:
 * * [ParameterizedType] - stores metadata about parametrized type as primitive type and list of generics
 * * [EntityReference] - stores metadata about reference to the another entity. It stores: entity name, is target entity child or not, connection type
 * * [PrimitiveType] - simple type, which stores name of the primitive class such as, for example, kotlin.List or kotlin.Int
 * * [CustomType] - simple type, which stores reference to the metadata of this custom type. For example, custom users data class
 *
 * @property [classBuilder] is used to build custom classes, if value type is [ValueType.JvmClass]
 */
private class ValueTypeMetadataBuilder(private val property: TypeProperty<*>,
                                       private val classBuilder: MetadataBuilder<ValueType.JvmClass<*>>,
                                       private val builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): MetadataBuilder<ValueType<*>> {
  override fun buildMetadata(obj: ValueType<*>): String {
    if (obj.isEntityRef(property)) {
      return obj.getRefType().buildEntityReference(property as ObjProperty<*, *>)
    }
    return obj.buildMetadata()
  }

  private fun ValueType<*>.buildMetadata(isNullable: Boolean = false): String {
    return when (this) {
      is ValueType.Optional<*> -> type.buildMetadata(true)
      is ValueType.Primitive<*>, is ValueType.Nothing, is ValueType.Any -> buildPrimitiveType(isNullable, builtPrimitiveTypes)
      is ValueType.Collection<*, *> -> buildParametrizedType(isNullable, builtPrimitiveTypes, elementType.buildMetadata())
      is ValueType.Map<*, *> -> buildParametrizedType(isNullable, builtPrimitiveTypes, keyType.buildMetadata(), valueType.buildMetadata(false))
      is ValueType.JvmClass<*> -> buildCustomTypeReference(classBuilder, isNullable)
      is ValueType.ObjRef<*> -> buildKnownClassReference(isNullable)
      else -> unsupportedType(this@buildMetadata)
    }
  }
}


private fun ObjClass<*>.buildEntity(propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getEntityMetadataConstructor(
    fqName = fullName,
    entityDataFqName = getJavaFullName(javaDataName, module.name),
    supertypes = allSuperClasses.map { it.fullName },
    properties = (allFieldsWithComputable + ownExtensions.filterNot { it.valueType.isEntityRef(it) }).map { propertyBuilder.buildMetadata(it) },
    extProperties = module.extensions
      .filter { it.valueType.isEntityRef(it) && it.valueType.getRefType().target == this }
      .map { propertyBuilder.buildMetadata(it) },
    isAbstract = isAbstract
  )


private fun ObjProperty<*, *>.buildOwnProperty(valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getOwnPropertyMetadataConstructor(
    name = name.withDoubleQuotes(),
    valueType = valueTypeBuilder.buildMetadata(valueType),
    isComputable = isComputable,
    isOpen = open,
    withDefault = withDefault,
    isKey = if (this is OwnProperty) this.isKey else false
  )

private fun ExtProperty<*, *>.buildExtProperty(valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getExtPropertyMetadataConstructor(
    name = name.withDoubleQuotes(),
    receiverFqn = receiver.fullName,
    valueType = valueTypeBuilder.buildMetadata(valueType),
    isComputable = isComputable,
    isOpen = open,
    withDefault = withDefault
  )

private fun ValueType.ClassProperty<*>.buildClassProperty(valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getOwnPropertyMetadataConstructor(
    name = name.withDoubleQuotes(),
    valueType = valueTypeBuilder.buildMetadata(valueType),
    isComputable = false,
    isOpen = false,
    withDefault = false,
    isKey = false
  )

private fun ValueType<*>.buildParametrizedType(isNullable: Boolean, builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>, vararg generics: String): String =
  getParameterizedTypeConstructor(primitive = buildPrimitiveType(isNullable, builtPrimitiveTypes), generics = generics.asList()) //TODO("Test this")

private fun ValueType<*>.buildPrimitiveType(isNullable: Boolean, builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): String {
  val buildPrimitiveType = BuiltPrimitiveType(javaPrimitiveType, isNullable)
  builtPrimitiveTypes.add(buildPrimitiveType)
  return buildPrimitiveType.getVariableName()
}

private fun ValueType.ObjRef<*>.buildKnownClassReference(isNullable: Boolean): String =
  getCustomTypeConstructor(isNullable, getKnownClassConstructor(target.fullName))

private fun ValueType.JvmClass<*>.buildCustomTypeReference(classBuilder: MetadataBuilder<ValueType.JvmClass<*>>, isNullable: Boolean): String =
  getCustomTypeConstructor(isNullable, classBuilder.buildMetadata(this))


private fun ValueType.ObjRef<*>.buildEntityReference(property: ObjProperty<*, *>): String =
  getEntityReferenceConstructor(
    entityFqName = getJavaFullName(target.name, target.module.name),
    isChild = child,
    connectionType = property.refsConnectionType(this@buildEntityReference),
    isNullable = property.valueType is ValueType.Optional<*>
  )


private fun ValueType<*>.isEntityRef(property: TypeProperty<*>): Boolean {
  return isRefType() && property is ObjProperty<*, *> && !property.isComputable && !property.withDefault
}


internal inline fun <reified T> unsupportedType(obj: T): String {
  throw UnsupportedOperationException("$obj type ${T::class.java} isn't supported")
}