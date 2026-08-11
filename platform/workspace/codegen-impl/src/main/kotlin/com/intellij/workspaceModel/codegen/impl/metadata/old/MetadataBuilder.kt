package com.intellij.workspaceModel.codegen.impl.metadata.old

import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.engine.GenerationException
import com.intellij.workspaceModel.codegen.impl.metadata.escapeDollar
import com.intellij.workspaceModel.codegen.impl.metadata.getFullName
import com.intellij.workspaceModel.codegen.impl.metadata.getJavaFullName
import com.intellij.workspaceModel.codegen.impl.metadata.javaPrimitiveType
import com.intellij.workspaceModel.codegen.impl.metadata.model.*
import com.intellij.workspaceModel.codegen.impl.metadata.withDoubleQuotes
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.javaDataName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.refsConnectionType
import kotlin.jvm.java

internal fun interface MetadataBuilder<T> {
  fun buildMetadata(context: GeneratorContext, obj: T): String
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
  override fun buildMetadata(context: GeneratorContext, obj: ObjClass<*>): String {
    val propertyBuilder = PropertyMetadataBuilder(builtPrimitiveTypes)
    return context.buildEntity(obj, propertyBuilder)
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

  override fun buildMetadata(context: GeneratorContext, obj: TypeProperty<*>): String {
    val valueTypeBuilder = createValueTypeBuilder(obj)
    return when (obj) {
      is OwnProperty<*, *> -> context.buildOwnProperty(obj, valueTypeBuilder)
      is ExtProperty<*, *> ->
        if (obj.valueType.isEntityRef(obj)) context.buildExtProperty(obj, valueTypeBuilder) else context.buildOwnProperty(obj, valueTypeBuilder)
      is ValueType.ClassProperty -> context.buildClassProperty(obj, valueTypeBuilder)
      else -> {
        throw GenerationException("$obj type ${obj::class.java} isn't supported")
      }
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
  // TODO: look into this and the parent class
  override fun buildMetadata(context: GeneratorContext, obj: ValueType<*>): String {
    if (obj.isEntityRef(property)) {
      val unwrapped = unwrapReferenceType(obj)
      if (unwrapped == null) {
        // No way to get problem location for reporter (yet)
        error(("$obj isn't a reference type. The method has to be called only for the reference type of fields"))
      }
      return context.buildEntityReference(unwrapped, property as ObjProperty<*, *>)
    }
    return context.buildMetadata(obj)
  }

  private fun GeneratorContext.buildMetadata(valueType: ValueType<*>, isNullable: Boolean = false): String {
    return when (valueType) {
      is ValueType.Optional<*> -> buildMetadata(valueType.type, true)
      is ValueType.Primitive<*>, is ValueType.Nothing, is ValueType.Any -> buildPrimitiveType(valueType, isNullable, builtPrimitiveTypes)
      is ValueType.Collection<*, *> -> buildParametrizedType(valueType,
                                                             isNullable,
                                                             builtPrimitiveTypes,
                                                             buildMetadata(valueType.elementType))
      is ValueType.Map<*, *> -> buildParametrizedType(valueType,
                                                      isNullable,
                                                      builtPrimitiveTypes,
                                                      buildMetadata(valueType.keyType), buildMetadata(valueType.valueType))
      is ValueType.JvmClass<*> -> buildCustomTypeReference(valueType, classBuilder, isNullable)
      is ValueType.ObjRef<*> -> buildKnownClassReference(valueType, isNullable)
      else -> {
        //context.reportError("$obj type ${obj::class.java} isn't supported", ???)
        //""
        throw GenerationException("$valueType type isn't supported")
      }
    }
  }
}


private fun GeneratorContext.buildEntity(objClass: ObjClass<*>, propertyBuilder: MetadataBuilder<TypeProperty<*>>): String {
  return getEntityMetadataConstructor(
    fqName = getFullName(objClass),
    entityDataFqName = getJavaFullName(objClass.javaDataName, objClass.module.implPackage),
    supertypes = objClass.allSuperClasses.map { getFullName(it) },
    properties = getAllPropertiesWithOwnExtensions(objClass).map { propertyBuilder.buildMetadata(this, it) },
    extProperties = objClass.module.extensions
      .filter { 
        val unwrapped = unwrapReferenceType(it.valueType)
        // isEntityRef == true && unwrapped == null should be impossible
        it.valueType.isEntityRef(it) && unwrapped != null && unwrapped.target == objClass 
      }
      .map { propertyBuilder.buildMetadata(this, it) },
    isAbstract = objClass.isAbstract
  )
}


private fun GeneratorContext.buildOwnProperty(objProperty: ObjProperty<*, *>, valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getOwnPropertyMetadataConstructor(
    name = objProperty.name.withDoubleQuotes(),
    valueType = valueTypeBuilder.buildMetadata(this, objProperty.valueType),
    isComputable = objProperty.isComputable,
    isOpen = objProperty.open,
    withDefault = objProperty.withDefault,
    isKey = if (objProperty is OwnProperty) objProperty.isKey else false
  )

private fun GeneratorContext.buildExtProperty(extProperty: ExtProperty<*, *>, valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getExtPropertyMetadataConstructor(
    name = extProperty.name.withDoubleQuotes(),
    receiverFqn = getFullName(extProperty.receiver),
    valueType = valueTypeBuilder.buildMetadata(this, extProperty.valueType),
    isComputable = extProperty.isComputable,
    isOpen = extProperty.open,
    withDefault = extProperty.withDefault
  )

private fun GeneratorContext.buildClassProperty(valueTypeClassProperty: ValueType.ClassProperty<*>, valueTypeBuilder: MetadataBuilder<ValueType<*>>): String =
  getOwnPropertyMetadataConstructor(
    name = valueTypeClassProperty.name.withDoubleQuotes(),
    valueType = valueTypeBuilder.buildMetadata(this, valueTypeClassProperty.valueType),
    isComputable = false,
    isOpen = false,
    withDefault = false,
    isKey = false
  )

private fun buildParametrizedType(
  valueType: ValueType<*>,
  isNullable: Boolean,
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>,
  vararg generics: String
): String {
  return getParameterizedTypeConstructor(primitive = buildPrimitiveType(valueType, isNullable, builtPrimitiveTypes),
                                         generics = generics.asList()) //TODO("Test this")
}

private fun buildPrimitiveType(valueType: ValueType<*>, isNullable: Boolean, builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): String {
  val buildPrimitiveType = BuiltPrimitiveType(valueType.javaPrimitiveType, isNullable)
  builtPrimitiveTypes.add(buildPrimitiveType)
  return buildPrimitiveType.getVariableName()
}

private fun GeneratorContext.buildKnownClassReference(valueTypeObjRef: ValueType.ObjRef<*>, isNullable: Boolean): String =
  getCustomTypeConstructor(isNullable, getKnownClassConstructor(getFullName(valueTypeObjRef.target)))

private fun GeneratorContext.buildCustomTypeReference(
  valueTypeJvmClass: ValueType.JvmClass<*>,
  classBuilder: MetadataBuilder<ValueType.JvmClass<*>>,
  isNullable: Boolean
): String {
  return getCustomTypeConstructor(isNullable, classBuilder.buildMetadata(this, valueTypeJvmClass))
}


private fun GeneratorContext.buildEntityReference(valueTypeObjRef: ValueType.ObjRef<*>, property: ObjProperty<*, *>): String =
  getEntityReferenceConstructor(
    entityFqName = getJavaFullName(valueTypeObjRef.target.name, valueTypeObjRef.target.module.name),
    isChild = valueTypeObjRef.child,
    connectionType = refsConnectionType(property, valueTypeObjRef),
    isNullable = property.valueType is ValueType.Optional<*>
  )