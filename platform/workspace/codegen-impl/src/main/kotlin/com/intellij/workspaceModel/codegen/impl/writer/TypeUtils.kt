// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaBuilderFqnName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName

private fun GeneratorContext.unsupportedPropertyType(objProperty: ObjProperty<*, *>, valueType: ValueType<*>): QualifiedName {
  reportPropertyError("$valueType type isn't supported", objProperty)
  return QualifiedName("")
}

fun GeneratorContext.getJavaType(objProperty: ObjProperty<*, *>, givenValueType: ValueType<*>? = null): QualifiedName {
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
    is ValueType.List<*> -> "List".toQualifiedName().appendSuffix("<${getJavaType(objProperty, valueType.elementType)}>")
    is ValueType.Set<*> -> "Set".toQualifiedName().appendSuffix("<${getJavaType(objProperty, valueType.elementType)}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName()
      .appendSuffix("<${getJavaType(objProperty, valueType.keyType)}, ${getJavaType(objProperty, valueType.valueType)}>")
    is ValueType.ObjRef -> valueType.target.javaFullName
    is ValueType.Optional<*> -> getJavaType(objProperty, valueType.type).appendSuffix("?")
    is ValueType.JvmClass -> valueType.kotlinClassName.toQualifiedName()
    else -> unsupportedPropertyType(objProperty, valueType)
  }
}

/**
 * Generate builder code with the generics like `<out Entity>` if they needed
 */
fun GeneratorContext.getJavaBuilderTypeWithGeneric(objProperty: ObjProperty<*, *>, givenValueType: ValueType<*>? = null): QualifiedName {
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
    is ValueType.List<*> -> "List".toQualifiedName().appendSuffix("<${getJavaBuilderTypeWithGeneric(objProperty, valueType.elementType)}>")
    is ValueType.Set<*> -> "Set".toQualifiedName().appendSuffix("<${getJavaBuilderTypeWithGeneric(objProperty, valueType.elementType)}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName()
      .appendSuffix("<${getJavaBuilderTypeWithGeneric(objProperty, valueType.keyType)}, ${
        getJavaBuilderTypeWithGeneric(objProperty,
                                      valueType.valueType)
      }>")
    is ValueType.ObjRef -> {
      val out = if (valueType.target.openness == ObjClass.Openness.abstract) "out " else ""
      valueType.target.javaBuilderFqnName.appendSuffix(if (valueType.target.builderWithTypeParameter) "<$out${
        getJavaType(objProperty,
                    valueType)
      }>"
                                                       else "")
    }
    is ValueType.Optional<*> -> getJavaBuilderTypeWithGeneric(objProperty, valueType.type).appendSuffix("?")
    is ValueType.JvmClass -> valueType.kotlinClassName.toQualifiedName()
    else -> unsupportedPropertyType(objProperty, valueType)
  }
}

fun GeneratorContext.getJavaMutableType(objProperty: ObjProperty<*, *>, givenValueType: ValueType<*>? = null): QualifiedName {
  return when (val valueType = givenValueType ?: objProperty.valueType) {
    is ValueType.List<*> -> "MutableList".toQualifiedName().appendSuffix("<${getJavaType(objProperty, valueType.elementType)}>")
    is ValueType.Set<*> -> "MutableSet".toQualifiedName().appendSuffix("<${getJavaType(objProperty, valueType.elementType)}>")
    is ValueType.Map<*, *> -> "MutableMap".toQualifiedName()
      .appendSuffix("<${getJavaType(objProperty, valueType.keyType)}, ${getJavaType(objProperty, valueType.valueType)}>")
    is ValueType.Optional<*> -> getJavaMutableType(objProperty, valueType.type).appendSuffix("?")
    else -> getJavaType(objProperty, valueType)
  }
}

fun GeneratorContext.getEntityType(objProperty: ObjProperty<*, *>, givenValueType: ValueType<*>? = null): QualifiedName {
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
    is ValueType.List<*> -> getEntityType(objProperty, valueType.elementType)
    is ValueType.Set<*> -> getEntityType(objProperty, valueType.elementType)
    is ValueType.ObjRef -> valueType.target.javaFullName
    is ValueType.Optional<*> -> getEntityType(objProperty, valueType.type)
    is ValueType.JvmClass -> valueType.kotlinClassName.toQualifiedName()
    else -> unsupportedPropertyType(objProperty, valueType)
  }
}
