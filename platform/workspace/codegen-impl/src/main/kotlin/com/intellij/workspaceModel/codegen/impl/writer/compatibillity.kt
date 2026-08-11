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
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.compatibleJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType

private val DEPRECATION = "${Deprecated::class.fqn}(message = \"Use new API instead\")"

fun CodeContext.compatibilityInvoke(
  mandatoryProperties: List<ObjProperty<*, *>>,
  javaFullName: QualifiedName,
  builderGeneric: String,
) {
  val builderSymbol = "$javaFullName.Builder$builderGeneric"
  annotation(DEPRECATION)
  if (mandatoryProperties.isNotEmpty()) {
    line("fun compatibilityInvoke(")
    mandatoryProperties.forEach { field ->
      line("${field.name}: ${getJavaType(field)},")
    }
    line("init: ($builderSymbol.() -> Unit)? = null,")
    section("): $builderSymbol") {
      line("val builder = builder() as $builderSymbol")
      for (property in mandatoryProperties) {
        val name = property.name
        if (property.valueType is ValueType.Set<*> && !property.valueType.isReferenceType()) {
          +"builder.$name = $name.${StorageCollection.toMutableWorkspaceSet}()"
        }
        else if (property.valueType is ValueType.List<*> && !property.valueType.isReferenceType()) {
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