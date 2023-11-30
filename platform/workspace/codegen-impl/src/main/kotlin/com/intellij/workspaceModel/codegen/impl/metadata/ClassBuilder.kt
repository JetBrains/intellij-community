package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.TypeProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.metadata.model.*
import com.intellij.workspaceModel.codegen.impl.metadata.model.getAbstractClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getEnumClassMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getKnownClassConstructor
import com.intellij.workspaceModel.codegen.impl.metadata.model.getObjectMetadataConstructor
import com.intellij.workspaceModel.codegen.impl.writer.KnownClass
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFinalSubClasses

/**
 * Collects metadata for custom classes and returns it as string of metadata instance.
 *
 * Supported classes types:
 * * final classes [ValueType.FinalClass]
 * * objects [ValueType.Object]
 * * enum entries [ValueType.EnumEntry]
 * * known classes [ValueType.Blob]
 * * abstract classes [ValueType.AbstractClass]
 * * enum classes [ValueType.Enum]
 *
 * [ClassMetadataBuilder] is used also to resolve cycled references between custom classes.
 * All cycled references are references to [KnownClass], because we have already processed this class.
 *
 * It also reduces memory usage, because for each [EntityMetadataEntity] we store the metadata of the custom class only one time.
 * Other references to this custom class in the current [EntityMetadataEntity] will be in the form of [KnownClass] (stores only string).
 *
 * E.g.:
 * * Users custom classes are:
 * *  data class FirstDataClass(val data: SecondDataClass)
 * *  data class SecondDataClass(val data: FirstDataClass)
 * *
 * * After processing we get:
 * *  Property of the FirstDataClass metadata ---> CustomType(typeMetadata = "Constructor of FinalClassMetadata for the SecondDataClass")
 * *  Property of the SecondDataClass metadata ---> CustomType(typeMetadata = KnownClass(name = "FirstDataClass"))
 */
internal class ClassMetadataBuilder(private val propertyBuilder: MetadataBuilder<TypeProperty<*>>): MetadataBuilder<ValueType.JvmClass<*>> {
  override fun buildMetadata(obj: ValueType.JvmClass<*>): String {
    return when (obj) {
      is ValueType.Blob<*> -> obj.buildKnownClass()
      is ValueType.FinalClass<*> -> obj.buildFinalClass(propertyBuilder)
      is ValueType.Object<*> -> obj.buildObject(propertyBuilder)
      is ValueType.AbstractClass<*> -> obj.buildAbstractClass(this)
      is ValueType.Enum<*> -> obj.buildEnum(propertyBuilder)
      else -> unsupportedType(obj)
    }
  }
}


private fun ValueType.JvmClass<*>.buildKnownClass(): String = getKnownClassConstructor(name)

private fun ValueType.FinalClass<*>.buildFinalClass(propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getClassMetadataConstructor(name, supertypes = superClasses, properties = properties.map { propertyBuilder.buildMetadata(it) })

private fun ValueType.Object<*>.buildObject(propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getObjectMetadataConstructor(name, supertypes = superClasses, properties = properties.map { propertyBuilder.buildMetadata(it) })

private fun ValueType.AbstractClass<*>.buildAbstractClass(classBuilder: MetadataBuilder<ValueType.JvmClass<*>>): String =
  getAbstractClassMetadataConstructor(name, supertypes = superClasses, subclasses = allFinalSubClasses.map { classBuilder.buildMetadata(it) })

private fun ValueType.Enum<*>.buildEnum(propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getEnumClassMetadataConstructor(
    fqName = name, supertypes = superClasses, values = values.allWithDoubleQuotesAndEscapedDollar(),
    properties = properties.map { propertyBuilder.buildMetadata(it) }
  )