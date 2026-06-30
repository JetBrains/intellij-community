// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.optInWorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.dsl.unsupportedTypeError
import com.intellij.workspaceModel.codegen.impl.metadata.getFullName
import com.intellij.workspaceModel.codegen.impl.writer.EntityMetadata
import com.intellij.workspaceModel.codegen.impl.writer.EntityStorageInstrumentation
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.MutableEntityStorage
import com.intellij.workspaceModel.codegen.impl.writer.SoftLinkable
import com.intellij.workspaceModel.codegen.impl.writer.StorageCollection
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityData
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityWithSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.collectionProperties
import com.intellij.workspaceModel.codegen.impl.writer.entitySourceFieldName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.hasSetter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isOverride
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.getAllReferenceProperties
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType
import com.intellij.workspaceModel.codegen.impl.writer.mandatoryProperties
import com.intellij.workspaceModel.codegen.impl.writer.referenceNameToSyntheticSymbolicIdFieldName
import com.intellij.workspaceModel.codegen.impl.writer.referencesInSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdField

val ObjClass<*>.javaDataName
  get() = "${name.replace(".", "")}Data"

val ObjClass<*>.isEntityWithSymbolicId: Boolean
  get() = superTypes.any {
    it is ObjClass<*> && (it.javaFullName.decoded == WorkspaceEntityWithSymbolicId.decoded || it.isEntityWithSymbolicId)
  }

fun Regex.getFirstMatch(input: String): String? {
  return matchEntire(input)?.groupValues?.getOrNull(1)
}

fun CodeContext.entityDataClassCode(objClass: ObjClass<*>) {
  val entityDataBaseClass = "${WorkspaceEntityData}<${objClass.javaFullName}>()"
  val referencesInSymbolicId = referencesInSymbolicId(objClass) ?: emptySet()
  val hasSoftLinks = objClass.hasSoftLinks() || referencesInSymbolicId.isNotEmpty()
  val softLinkable = if (hasSoftLinks) SoftLinkable else null

  optInWorkspaceEntityInternalApi()
  val supers = listOfNotNull(entityDataBaseClass, softLinkable?.encodedString).joinToString(separator = ", ")
  section("internal class ${objClass.javaDataName} : $supers") {
    for (property in getAllProperties(objClass, withSymbolicId = false, withRefs = false, withEntitySource = false)) {
      implWsDataFieldCode(property)
    }

    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      val referencedSymbolicId = unwrapReferenceType(reference.valueType)?.target?.symbolicIdField
      if (referencedSymbolicId == null) {
        reportPropertyError("Cannot find reference ${reference.name} or the referenced entity symbolic id", reference)
      }
      else {
        line("lateinit var $syntheticName: ${getJavaType(referencedSymbolicId)}")
      }
    }

    for (property in getAllProperties(objClass,
                                      withSymbolicId = false,
                                      withRefs = false,
                                      withEntitySource = false,
                                      withOptional = false,
                                      withDefault = false)) {
      dataPropertyIsInitializedCode(property)
    }

    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      val capitalizedSyntheticName = syntheticName.replaceFirstChar { it.titlecaseChar() }
      line("internal fun is${capitalizedSyntheticName}Initialized(): Boolean = ::$syntheticName.isInitialized")
    }

    softLinksCode(objClass, hasSoftLinks, referencesInSymbolicId)

    section("override fun wrapAsModifiable(diff: ${MutableEntityStorage}): ${WorkspaceEntity.Builder}<${objClass.javaFullName}>") {
      line("val modifiable = ${objClass.javaImplBuilderName}(null)")
      line("modifiable.diff = diff")
      line("modifiable.id = createEntityId()")
      line("return modifiable")
    }

    section("override fun createEntity(snapshot: $EntityStorageInstrumentation): ${objClass.javaFullName}") {
      line("val entityId = createEntityId()")
      section("return snapshot.initializeEntity(entityId)") {
        line("val entity = ${objClass.javaImplName}(this)")
        line("entity.snapshot = snapshot")
        line("entity.id = entityId")
        line("entity")
      }
    }

    section("override fun getMetadata(): $EntityMetadata") {
      line("return ${MetadataStorage.IMPL_NAME}.${MetadataStorage.getMetadataByTypeFqn}(${getFullName(objClass)}) as $EntityMetadata")
    }

    val collectionFields = collectionProperties(objClass)
    if (collectionFields.isNotEmpty()) {
      section("override fun clone(): ${objClass.javaDataName}") {
        val fieldName = "clonedEntity"
        line("val $fieldName = super.clone()")
        line("$fieldName as ${objClass.javaDataName}")
        collectionFields.forEach { field ->
          if (field.valueType is ValueType.Set<*>) {
            line("$fieldName.${field.name} = $fieldName.${field.name}.${StorageCollection.toMutableWorkspaceSet}()")
          }
          else {
            line("$fieldName.${field.name} = $fieldName.${field.name}.${StorageCollection.toMutableWorkspaceList}()")
          }
        }
        line("return $fieldName")
      }
    }

    section("override fun getEntityInterface(): Class<out ${WorkspaceEntity}>") {
      line("return ${objClass.name}::class.java")
    }

    section("override fun createDetachedEntity(parents: List<${WorkspaceEntity.Builder}<*>>): ${WorkspaceEntity.Builder}<*>") {
      val noRefs = getAllProperties(objClass, withSymbolicId = false, withRefs = false)
      val mandatoryFields = mandatoryProperties(objClass)
      val constructor = mandatoryFields.joinToString(", ") { it.name }.let { if (it.isNotBlank()) "($it)" else "" }
      val optionalFields = noRefs.filterNot { it in mandatoryFields }
      val parentFields = getAllReferenceProperties(objClass, onlyParents = true)

      if (optionalFields.isEmpty() && parentFields.isEmpty()) {
        line("return ${objClass.javaFullName}$constructor")
      }
      else {
        section("return ${objClass.javaFullName}$constructor") {
          optionalFields.forEach { field ->
            val toMutable = when (field.valueType) {
              is ValueType.List<*> -> ".${StorageCollection.toMutableWorkspaceList}()"
              is ValueType.Set<*> -> ".${StorageCollection.toMutableWorkspaceSet}()"
              else -> ""
            }
            line("this.${field.name} = this@${objClass.javaDataName}.${field.name}$toMutable")
          }
          parentFields.forEach { parentField ->
            val parentType = parentField.valueType
            if (parentType is ValueType.Optional) {
              line("this.${parentField.name} = parents.filterIsInstance<${
                getJavaBuilderTypeWithGeneric(parentField,
                                              parentType.type)
              }>().singleOrNull()")
            }
            else {
              line("parents.filterIsInstance<${getJavaBuilderTypeWithGeneric(parentField)}>().singleOrNull()?.let { this.${parentField.name} = it }")
            }
          }
        }
      }
    }

    section("override fun getRequiredParents(): List<Class<out $WorkspaceEntity>>") {
      line("val res = mutableListOf<Class<out $WorkspaceEntity>>()")
      val requiredParents = getAllReferenceProperties(objClass, onlyParents = true).filter { it.valueType !is ValueType.Optional }
      for (parentProperty in requiredParents) {
        line("res.add(${getJavaType(parentProperty)}::class.java)")
      }
      line("return res")
    }

    val keyFields = getAllProperties(objClass).filter { it.isKey }
    section("override fun equals(other: Any?): Boolean") {
      line("if (other == null) return false")
      line("if (this.javaClass != other.javaClass) return false")

      line("other as ${objClass.javaDataName}")

      for (property in getAllProperties(objClass, withSymbolicId = false, withRefs = false)) {
        val name = property.name
        +"if (this.$name != other.$name) return false"
      }

      line("return true")
    }

    section("override fun equalsIgnoringEntitySource(other: Any?): Boolean") {
      line("if (other == null) return false")
      line("if (this.javaClass != other.javaClass) return false")

      line("other as ${objClass.javaDataName}")

      for(property in getAllProperties(objClass, withSymbolicId = false, withRefs = false, withEntitySource = false)) {
        val name = property.name
        +"if (this.$name != other.$name) return false"
      }

      line("return true")
    }

    section("override fun hashCode(): Int") {
      line("var result = entitySource.hashCode()")
      for (property in getAllProperties(objClass, withSymbolicId = false, withRefs = false, withEntitySource = false)) {
        val name = property.name
        +"result = 31 * result + $name.hashCode()"
      }
      line("return result")
    }

    section("override fun hashCodeIgnoringEntitySource(): Int") {
      line("var result = javaClass.hashCode()")
      for(property in getAllProperties(objClass, withSymbolicId = false, withRefs = false, withEntitySource = false)) {
        val name = property.name
        +"result = 31 * result + $name.hashCode()"
      }
      line("return result")
    }

    if (keyFields.isNotEmpty()) {
      section("override fun equalsByKey(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this.javaClass != other.javaClass) return false")

        line("other as ${objClass.javaDataName}")

        for (keyProperty in keyFields) {
          val name = keyProperty.name
          +"if (this.$name != other.$name) return false"
        }

        line("return true")
      }
      section("override fun hashCodeByKey(): Int") {
        line("var result = javaClass.hashCode()")
        for (keyProperty in keyFields) {
          val name = keyProperty.name
          +"result = 31 * result + $name.hashCode()"
        }
        line("return result")
      }
    }
  }
}

internal fun CodeContext.implWsDataFieldCode(objProperty: ObjProperty<*, *>) {
  if (objProperty.hasSetter) {
    // TODO: name?
    if (objProperty.isOverride && objProperty.name !in listOf("name", entitySourceFieldName)) {
      implWsBlockingCodeOverride(objProperty)
    }
    else {
      implWsDataBlockCode(objProperty)
    }
  }
  else {
    val expression = when (val kind = objProperty.valueKind) {
      is ObjProperty.ValueKind.Computable -> kind.expression
      is ObjProperty.ValueKind.WithDefault -> kind.value
      else -> {
        reportPropertyError("Property has wrong kind: ${objProperty.valueKind}", objProperty)
        return
      }
    }
    val changeToMutable = objProperty.valueType is ValueType.Collection<*, *> && !objProperty.valueType.isReferenceType()
    val javaType = if (changeToMutable) getJavaMutableType(objProperty) else getJavaType(objProperty)
    val toMutable = when {
      changeToMutable && objProperty.valueType is ValueType.List<*> -> ".${StorageCollection.toMutableWorkspaceList}()"
      changeToMutable && objProperty.valueType is ValueType.Set<*> -> ".${StorageCollection.toMutableWorkspaceSet}()"
      else -> ""
    }
    if (expression.startsWith("=")) {
      +"${explicitApiModifier}var ${objProperty.javaName}: $javaType $expression$toMutable"
    }
    else {
      +"${explicitApiModifier}var ${objProperty.javaName}: $javaType = $expression$toMutable"
    }
  }
}

private fun GeneratorContext.getPrimitiveDefaultValue(
  objProperty: ObjProperty<*, *>,
  valueType: ValueType.Primitive<*>,
  isOptional: Boolean,
): String {
  if (isOptional) return "null"
  return when (valueType) {
    ValueType.Int, ValueType.Long, ValueType.Short, ValueType.Byte -> "0"
    ValueType.UShort, ValueType.UInt, ValueType.ULong, ValueType.UByte -> "0u"
    ValueType.Boolean -> "false"
    ValueType.Char -> "0.toChar()"
    ValueType.Float -> "0f"
    ValueType.Double -> "0.0"
    ValueType.String -> {
      reportPropertyError("String should be `lateinit` without a default value", objProperty)
      ""
    }
  }
}

private fun CodeContext.implWsDataBlockCode(objProperty: ObjProperty<*, *>) {
  val rawType = objProperty.valueType
  val (isOptional, propertyType) = if (rawType is ValueType.Optional<*>)
    true to rawType.type
  else
    false to rawType
  val name = objProperty.javaName
  when (propertyType) {
    is ValueType.Primitive<*> -> {
      if (propertyType is ValueType.String && !isOptional) {
        +"${explicitApiModifier}lateinit var $name: String"
        return
      }
      val javaType = getJavaType(objProperty, propertyType)
      val defaultValue = getPrimitiveDefaultValue(objProperty, propertyType, isOptional)
      val optionalSuffix = if (isOptional) "?" else "" // non-optional propertyType is passed to `getJavaType`
      +"${explicitApiModifier}var $name: $javaType$optionalSuffix = $defaultValue"
    }
    is ValueType.ObjRef<*> -> {
      reportPropertyError("Reference type at EntityData not supported", objProperty)
    }
    is ValueType.Collection<*, *> -> {
      if (propertyType.isReferenceType()) {
        reportPropertyError("Reference type at EntityData not supported", objProperty)
        return
      }
      if (isOptional) {
        +"var $name: ${getJavaMutableType(objProperty, propertyType)}? = null"
      }
      else {
        +"lateinit var $name: ${getJavaMutableType(objProperty, propertyType)}"
      }
    }
    is ValueType.Map<*, *>, is ValueType.JvmClass -> {
      if (isOptional) {
        +"var $name: ${getJavaType(objProperty, propertyType)}? = null"
      }
      else {
        +"lateinit var $name: ${getJavaType(objProperty, propertyType)}"
      }
    }
    is ValueType.Optional<*> -> {
      reportPropertyError("Optional properties should be unwrapped at this point in data class", objProperty)
    }
    else -> {
      reportPropertyError("Unsupported property type: $propertyType", objProperty)
    }
  }
}

private fun CodeContext.dataPropertyIsInitializedCode(objProperty: ObjProperty<*, *>) {
  when (objProperty.valueType) {
    ValueType.Int, ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong,
      -> { /* do nothing */
    }
    is ValueType.String, is ValueType.JvmClass, is ValueType.Collection<*, *>, is ValueType.Map<*, *> -> {
      val capitalizedPropertyName = objProperty.javaName.replaceFirstChar { it.titlecaseChar() }
      +"internal fun is${capitalizedPropertyName}Initialized(): Boolean = ::${objProperty.javaName}.isInitialized"
    }
    else -> unsupportedTypeError(objProperty.valueType, objProperty)
  }
}