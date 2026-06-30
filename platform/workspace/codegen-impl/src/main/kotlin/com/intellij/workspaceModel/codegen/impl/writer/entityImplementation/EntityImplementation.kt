// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.apiVersionAnnotation
import com.intellij.workspaceModel.codegen.impl.dsl.implVersionAnnotation
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.dsl.optInWorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.dsl.packageDirective
import com.intellij.workspaceModel.codegen.impl.dsl.unsupportedTypeError
import com.intellij.workspaceModel.codegen.impl.writer.ConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.EntityStorageInstrumentationApi
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.writer.checkReference
import com.intellij.workspaceModel.codegen.impl.writer.entitySourceFieldName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.hasSetter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isOverride
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.getAllReferenceProperties
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdFieldName
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdImplCode
import com.intellij.workspaceModel.codegen.impl.writer.toQualifiedName

fun CodeContext.entityImplementationClassCode(objClass: ObjClass<*>) {
  val inheritanceModifier = when {
    objClass.openness.extendable && !objClass.openness.instantiatable -> "abstract "
    objClass.openness.extendable && objClass.openness.instantiatable -> "open "
    else -> ""
  }

  val entityReferenceProperties = getAllReferenceProperties(objClass)
  val hasConnections = entityReferenceProperties.isNotEmpty()

  annotation("file:OptIn($EntityStorageInstrumentationApi::class)")
  packageDirective(objClass.module.implPackage)
  +"import ${objClass.module}.${objClass.defaultJavaBuilderName}"
  entityImplementationAnnotations(objClass)
  section("internal ${inheritanceModifier}class ${objClass.javaImplName}(private val dataSource: ${objClass.javaDataName}): ${objClass.javaFullName}, ${WorkspaceEntityBase}(dataSource)") {
    if (hasConnections) {
      section("private companion object") {
        for (refField in entityReferenceProperties) {
          connectionInitialization(refField)
        }
        +entityReferenceProperties.joinToString(prefix = "private val connections = listOf<$ConnectionId>(",
                                                postfix = ")",
                                                separator = ",") { connectionIdForReference(it) }
      }
    }

    +symbolicIdImplCode(objClass)

    for (property in getAllProperties(objClass).filter { it.name !in listOf(entitySourceFieldName, symbolicIdFieldName) }) {
      implWsEntityFieldCode(property)
    }

    +"override val $entitySourceFieldName: EntitySource"
    section("get()") {
      +"readField(\"$entitySourceFieldName\")"
      +"return dataSource.entitySource"
    }

    section("override fun connectionIdList(): List<${ConnectionId}>") {
      if (hasConnections) +"return connections"
      else +"return emptyList()"
    }

    entityBuilderImplementationCode(objClass, hasConnections)
  }
}

private fun CodeContext.entityImplementationAnnotations(objClass: ObjClass<*>) {
  additionalAnnotations(objClass)
  apiVersionAnnotation()
  implVersionAnnotation()
  optInWorkspaceEntityInternalApi()
}

fun CodeContext.implWsEntityFieldCode(objProperty: ObjProperty<*, *>) {
  if (objProperty.hasSetter) {
    if (objProperty.isOverride && objProperty.name !in listOf("name", entitySourceFieldName)) {
      implWsBlockingCodeOverride(objProperty)
    }
    else {
      implWsBlockCode(objProperty)
    }
  }
  else {
    +"override var ${objProperty.javaName}: ${getJavaType(objProperty)} = dataSource.${objProperty.javaName}"
  }
}

internal fun CodeContext.implWsBlockCode(objProperty: ObjProperty<*, *>) {
  val rawType = objProperty.valueType
  val (isOptional, propertyType) = if (rawType is ValueType.Optional<*>)
    true to rawType.type
  else
    false to rawType
  val name = objProperty.javaName
  val optionalSuffix = if (isOptional) "?" else ""
  when (propertyType) {
    is ValueType.Primitive<*> -> {
      +"override val $name: ${getJavaType(objProperty, propertyType)}$optionalSuffix"
      section("get()") {
        +"readField(\"$name\")"
        +"return dataSource.$name"
      }
    }
    is ValueType.ObjRef -> {
      val notNullAssertion = if (!isOptional) " ?: error(\"Parent $name not found for ${objProperty.receiver.name}\")" else ""
      +"override val $name: ${getJavaType(objProperty, propertyType)}$optionalSuffix"
      +"get() = snapshot.${refsConnectionMethodCode(objProperty)} as? ${getJavaType(objProperty, propertyType)}$notNullAssertion"
    }
    is ValueType.List<*> -> {
      if (propertyType.isReferenceType()) {
        val connectionId = connectionIdForReference(objProperty)
        if (isOptional) {
          reportPropertyError("Nullable reference lists are prohibited", objProperty)
          return
        }
        val notNullAssertion = " ?: error(\"Children list $name not found for ${objProperty.receiver.name}\")"
        val cast = " as? Sequence<${getJavaType(objProperty, propertyType.elementType)}>)?.toList()"
        +"override val $name: ${getJavaType(objProperty, propertyType)}"
        +"get() = (snapshot.${Instrumentation.getManyChildren}($connectionId, this)$cast$notNullAssertion"
      }
      else {
        +"override val $name: ${getJavaType(objProperty, propertyType)}$optionalSuffix"
        section("get()") {
          +"readField(\"$name\")"
          +"return dataSource.$name"
        }
      }
    }
    is ValueType.Set<*> -> {
      if (propertyType.isReferenceType()) {
        reportPropertyError("Set of references is not supported", objProperty)
        return
      }
      +"override val ${objProperty.javaName}: ${getJavaType(objProperty, propertyType)}$optionalSuffix"
      section("get()") {
        +"readField(\"$name\")"
        +"return dataSource.$name"
      }
    }
    is ValueType.Map<*, *> -> {
      +"override val $name: ${getJavaType(objProperty, propertyType)}$optionalSuffix"
      section("get()") {
        +"readField(\"$name\")"
        +"return dataSource.$name"
      }
    }
    is ValueType.JvmClass -> {
      +"override val $name: ${propertyType.kotlinClassName.toQualifiedName()}$optionalSuffix"
      section("get()") {
        +"readField(\"$name\")"
        +"return dataSource.$name"
      }
    }
    is ValueType.Optional<*> -> {
      reportPropertyError("Optional properties should be unwrapped at this point in implementation", objProperty)
    }
    else -> {
      unsupportedTypeError(propertyType, objProperty)
    }
  }
}

internal fun CodeContext.implWsBlockingCodeOverride(objProperty: ObjProperty<*, *>) {
  val originalField = objProperty.receiver.refsFields.first { getJavaType(it) == getJavaType(objProperty) }
  val connectionName = connectionIdForReference(originalField)
  var valueType = getReferencedField(objProperty)?.valueType ?: return
  if (valueType is ValueType.Optional<*>) {
    valueType = valueType.type
  }
  // TODO
  val getterName = when (valueType) {
    is ValueType.List<*> -> if (objProperty.receiver.openness.extendable)
      Instrumentation.getParent
    else
      Instrumentation.getParent
    is ValueType.ObjRef<*> -> if (objProperty.receiver.openness.extendable)
      Instrumentation.getParent
    else
      Instrumentation.getParent
    else -> {
      reportPropertyError("Unsupported reference type", objProperty)
      return
    }
  }
  +"${explicitApiModifier}override val ${objProperty.name}: ${getJavaType(objProperty)}"
  +"get() = snapshot.$getterName($connectionName, this) as? ${getJavaType(objProperty)} ?: error(\"Parent ${objProperty.name} not found for ${objProperty.receiver.name}\")"
}

internal fun GeneratorContext.getReferencedField(objProperty: ObjProperty<*, *>): ObjProperty<*, *>? {
  checkReference(objProperty)
  val ref = unwrapReferenceType(objProperty.valueType)
  if (ref == null) {
    notReferenceError("getReferencedField", objProperty)
    return null
  }
  // TODO
  val declaredReferenceFromChild = try {
    ref.target.refsFields.filter { unwrapReferenceType(it.valueType)!!.target == objProperty.receiver && it != objProperty } +
    setOf(ref.target.module,
          objProperty.receiver.module).flatMap { it.extensions }
      .filter { unwrapReferenceType(it.valueType)!!.target == objProperty.receiver && it.receiver == ref.target && it != objProperty }
  }
  catch (e: NullPointerException) {
    notReferenceError("getReferencedField, ${e.message}", objProperty)
    return null
  }
  val referencedField = declaredReferenceFromChild[0]
  return referencedField
}
