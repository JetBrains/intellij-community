// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.dsl.unsupportedTypeError
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.lineComment
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.compatibleJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.requiresModuleId
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType

private val DEPRECATION = "${Deprecated::class.fqn}(message = \"Use new API instead\")"
private val FAKE_MODULE_ID_PROPERTY =
  PropertyData("moduleId", ValueType.Any, QualifiedName("#uC03o#com.intellij.platform.workspace.jps.entities@@ModuleId#ModuleId"))

fun CodeContext.compatibilityInvoke(
  mandatoryProperties: List<ObjProperty<*, *>>,
  objClass: ObjClass<*>,
  builderGeneric: String,
) {
  val builderSymbol = "${objClass.javaFullName}.Builder$builderGeneric"
  annotation(DEPRECATION)
  val convertedGiven = mandatoryProperties.map { PropertyData(it.name, it.valueType, getJavaType(it)) }
  val requiresModuleId = objClass.requiresModuleId
  val propertiesData = if (requiresModuleId) {
    listOf(FAKE_MODULE_ID_PROPERTY) + convertedGiven
  }
  else {
    convertedGiven
  }
  if (propertiesData.isNotEmpty()) {
    line("fun compatibilityInvoke(")
    for (propertyData in propertiesData) {
      line("${propertyData.name}: ${propertyData.javaType},")
    }
    line("init: ($builderSymbol.() -> Unit)? = null,")
    section("): $builderSymbol") {
      line("val builder = builder() as $builderSymbol")
      for (propertyData in propertiesData) {
        val name = propertyData.name
        if (requiresModuleId && name == FAKE_MODULE_ID_PROPERTY.name) continue
        if (propertyData.valueType is ValueType.Set<*> && !propertyData.valueType.isReferenceType()) {
          +"builder.$name = $name.${StorageCollection.toMutableWorkspaceSet}()"
        }
        else if (propertyData.valueType is ValueType.List<*> && !propertyData.valueType.isReferenceType()) {
          +"builder.$name = $name.${StorageCollection.toMutableWorkspaceList}()"
        }
        else {
          +"builder.$name = $name"
        }
      }
      line("init?.invoke(builder)")
      line("return builder")
    }
  }
  else {
    section("${explicitApiModifier}fun compatibilityInvoke(init: ($builderSymbol.() -> Unit)? = null): $builderSymbol") {
      line("val builder = builder() as $builderSymbol")
      line("init?.invoke(builder)")
      line("return builder")
    }
  }
}

fun CodeContext.generateCompatibilityConstructorCode(objClass: ObjClass<*>) {
  if (!objClass.requiresModuleId) return
  lineComment("IJPL-150365")
  val mandatoryProperties = mandatoryProperties(objClass)
  val builderGeneric = if (objClass.openness.extendable) "<${objClass.javaFullName}>" else ""
  val javaBuilderName = objClass.defaultJavaBuilderName

  val propertiesData = listOf(FAKE_MODULE_ID_PROPERTY) + mandatoryProperties.map { PropertyData(it.name, it.valueType, getJavaType(it)) }

  annotation("${Deprecated::class.fqn}(message = \"Use new constructor without moduleId\")")
  additionalAnnotations(objClass)
  annotation(JvmOverloads::class.fqn.toString())
  annotation("${JvmName::class.fqn}(\"create${objClass.name}\")")
  line("${explicitApiModifier}fun ${objClass.name}(")
  for (property in propertiesData) {
    line("${property.name}: ${property.javaType},")
  }
  line("init: ($javaBuilderName$builderGeneric.() -> Unit)? = null,")
  line("): $javaBuilderName = ${objClass.name}Type(${mandatoryProperties.joinToString(", ") { it.name }}, init)")
}

private data class PropertyData(val name: String, val valueType: ValueType<*>, val javaType: QualifiedName)

fun CodeContext.compatibilityModifyCode(objClass: ObjClass<*>) {
  annotation(DEPRECATION)
  additionalAnnotations(objClass)
  line("${explicitApiModifier}fun ${MutableEntityStorage}.modify${objClass.name}(")
  line("entity: ${objClass.name},")
  line("modification: ${objClass.compatibleJavaBuilderName}.() -> Unit,")
  line("): ${objClass.name} {")
  line("return modifyEntity(${objClass.compatibleJavaBuilderName}::class.java, entity, modification)")
  line("}")
}

fun CodeContext.compatibilityExtensionWsCode(extProperty: ExtProperty<*, *>) {
  val unwrappedType = unwrapReferenceType(extProperty.valueType)
  if (unwrappedType == null) {
    notReferenceError("extension", extProperty)
    return
  }
  val isChild = unwrappedType.child
  val generic = if (extProperty.receiver.builderWithTypeParameter) "<out ${extProperty.receiver.javaFullName}>" else ""
  if (extProperty.annotations.any { it.fqName == Internal.decoded }) {
    annotation("get:$Internal")
    annotation("set:$Internal")
  }
  annotation(DEPRECATION)
  val propertyType = getCompatibilityJavaBuilderTypeWithGeneric(extProperty)
  if (!isChild) annotation(Parent.toString())
  sectionNoBrackets("${explicitApiModifier}var ${extProperty.receiver.compatibleJavaBuilderName}$generic.${extProperty.name}: $propertyType") {
    line("get() = (this as ${extProperty.receiver.defaultJavaBuilderName}$generic).${extProperty.name} as $propertyType")
    section("set(value)") {
      line("(this as ${extProperty.receiver.defaultJavaBuilderName}$generic).${extProperty.name} = value")
    }
  }
}

private val ObjClass<*>.compatibilityJavaBuilderFqnName: QualifiedName
  get() = fqn(module.name, "$name.Builder")

private fun GeneratorContext.getCompatibilityJavaBuilderTypeWithGeneric(
  objProperty: ObjProperty<*, *>,
  givenValueType: ValueType<*>? = null,
): QualifiedName {
  return when (val valueType = givenValueType ?: objProperty.valueType) {
    ValueType.Boolean -> "Boolean".toQualifiedName()
    ValueType.Int -> "Int".toQualifiedName()
    ValueType.String -> "String".toQualifiedName()
    ValueType.Char -> "Char".toQualifiedName()
    ValueType.Long -> "Long".toQualifiedName()
    ValueType.Float -> "Float".toQualifiedName()
    ValueType.Double -> "Double".toQualifiedName()
    ValueType.Short -> "Short".toQualifiedName()
    ValueType.Byte -> "Byte".toQualifiedName()
    ValueType.UByte -> "UByte".toQualifiedName()
    ValueType.UShort -> "UShort".toQualifiedName()
    ValueType.UInt -> "UInt".toQualifiedName()
    ValueType.ULong -> "ULong".toQualifiedName()
    is ValueType.List<*> -> "List".toQualifiedName()
      .appendSuffix("<${getCompatibilityJavaBuilderTypeWithGeneric(objProperty, valueType.elementType)}>")
    is ValueType.Set<*> -> "Set".toQualifiedName()
      .appendSuffix("<${getCompatibilityJavaBuilderTypeWithGeneric(objProperty, valueType.elementType)}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName()
      .appendSuffix("<${getCompatibilityJavaBuilderTypeWithGeneric(objProperty, valueType.keyType)}, ${
        getCompatibilityJavaBuilderTypeWithGeneric(objProperty, valueType.valueType)
      }>")
    is ValueType.ObjRef -> {
      val out = if (valueType.target.openness == ObjClass.Openness.abstract) "out " else ""
      val suffix = if (valueType.target.builderWithTypeParameter) "<$out${getJavaType(objProperty, valueType)}>" else ""
      valueType.target.compatibilityJavaBuilderFqnName.appendSuffix(suffix)
    }

    is ValueType.Optional<*> -> getCompatibilityJavaBuilderTypeWithGeneric(objProperty, valueType.type).appendSuffix("?")
    is ValueType.JvmClass -> valueType.kotlinClassName.toQualifiedName()
    else -> {
      unsupportedTypeError(valueType, objProperty)
      QualifiedName("")
    }
  }
}