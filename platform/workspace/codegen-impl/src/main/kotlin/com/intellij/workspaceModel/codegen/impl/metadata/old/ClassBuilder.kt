package com.intellij.workspaceModel.codegen.impl.metadata.old

import com.intellij.workspaceModel.codegen.deft.meta.TypeProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.metadata.allWithDoubleQuotesAndEscapedDollar
import com.intellij.workspaceModel.codegen.impl.metadata.getName
import com.intellij.workspaceModel.codegen.impl.metadata.getSuperClasses
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
 * * enum entries [ValueType.Enum]
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
internal class ClassMetadataBuilder(private val propertyBuilder: MetadataBuilder<TypeProperty<*>>):
  MetadataBuilder<ValueType.JvmClass<*>> {
  override fun buildMetadata(context: GeneratorContext, obj: ValueType.JvmClass<*>): String {
    return when (obj) {
      is ValueType.Blob<*> -> context.buildKnownClass(obj)
      is ValueType.FinalClass<*> -> context.buildFinalClass(obj, propertyBuilder)
      is ValueType.Object<*> -> context.buildObject(obj, propertyBuilder)
      is ValueType.AbstractClass<*> -> context.buildAbstractClass(obj, this)
      is ValueType.Enum<*> -> context.buildEnum(obj, propertyBuilder)
    }
  }

  companion object {
    fun newInstance(builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>): MetadataBuilder<ValueType.JvmClass<*>> =
      PropertyMetadataBuilder(builtPrimitiveTypes).classBuilder
  }
}


private fun GeneratorContext.buildKnownClass(valueTypeJvmClass: ValueType.JvmClass<*>): String = getKnownClassConstructor(
  getName(valueTypeJvmClass))

private fun GeneratorContext.buildFinalClass(valueTypeFinalClass: ValueType.FinalClass<*>, propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getClassMetadataConstructor(getName(valueTypeFinalClass), supertypes = getSuperClasses(valueTypeFinalClass), properties = valueTypeFinalClass.properties.map { propertyBuilder.buildMetadata(this, it) })

private fun GeneratorContext.buildObject(valueTypeObject: ValueType.Object<*>, propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getObjectMetadataConstructor(getName(valueTypeObject), supertypes = getSuperClasses(valueTypeObject), properties = valueTypeObject.properties.map { propertyBuilder.buildMetadata(this, it) })

private fun GeneratorContext.buildAbstractClass(valueTypeAbstractClass: ValueType.AbstractClass<*>, classBuilder: MetadataBuilder<ValueType.JvmClass<*>>): String =
  getAbstractClassMetadataConstructor(getName(valueTypeAbstractClass), supertypes = getSuperClasses(valueTypeAbstractClass), subclasses = valueTypeAbstractClass.allFinalSubClasses.map { classBuilder.buildMetadata(this, it) })

private fun GeneratorContext.buildEnum(valueTypeEnum: ValueType.Enum<*>, propertyBuilder: MetadataBuilder<TypeProperty<*>>): String =
  getEnumClassMetadataConstructor(
    fqName = getName(valueTypeEnum), supertypes = getSuperClasses(valueTypeEnum), values = allWithDoubleQuotesAndEscapedDollar(valueTypeEnum.values),
    properties = valueTypeEnum.properties.map { propertyBuilder.buildMetadata(this, it) }
  )