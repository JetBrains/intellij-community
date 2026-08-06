// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.dsl.unsupportedTypeError
import com.intellij.workspaceModel.codegen.impl.writer.EntityLink
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.LibraryRoot
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceList
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceSet
import com.intellij.workspaceModel.codegen.impl.writer.SdkRoot
import com.intellij.workspaceModel.codegen.impl.writer.VirtualFileUrl
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType

fun CodeContext.getImplWsBuilderFieldCode(
  receiver: ObjClass<*>,
  property: ObjProperty<*, *>,
  referencesInSymbolicId: Set<OwnProperty<*, *>>?
) {
  if (property.valueType.isReferenceType()) {
    entityReferencePropertyBuilderCode(receiver, property, referencesInSymbolicId)
  }
  else {
    implWsBuilderBlockingCode(receiver, property.valueType, property)
  }
}

private fun CodeContext.unexpectedReference(objProperty: ObjProperty<*, *>) {
  reportPropertyError("Unexpected reference while building regular property code", objProperty)
}

private fun CodeContext.implWsBuilderBlockingCode(
  receiver: ObjClass<*>,
  valueType: ValueType<*>,
  objProperty: ObjProperty<*, *>,
  optionalSuffix: String = "",
) {
  when (valueType) {
    ValueType.Boolean, ValueType.Int, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double, ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> {
      +"override var ${objProperty.javaName}: ${getJavaMutableType(objProperty)}$optionalSuffix"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    // TODO: why String is separate from the above? What about optionalSuffix?
    ValueType.String -> {
      +"override var ${objProperty.javaName}: ${getJavaMutableType(objProperty)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    is ValueType.ObjRef -> {
      unexpectedReference(objProperty)
      return
    }

    is ValueType.List<*> -> {
      val elementType = valueType.elementType
      if (elementType.isReferenceType()) {
        unexpectedReference(objProperty)
        return
      }
      +"private val ${objProperty.javaName}Updater: (value: List<${getJavaType(objProperty, elementType)}>) -> Unit = { value ->"
      +elementType.addVirtualFileIndex(objProperty)
      +"changedProperty.add(\"${objProperty.javaName}\")"
      +"}"
      +"override var ${objProperty.javaName}: MutableList<${getJavaType(objProperty, elementType)}>"
      section("get()") {
        +"val collection_${objProperty.javaName} = getEntityData().${objProperty.javaName}"
        +"if (collection_${objProperty.javaName} !is ${MutableWorkspaceList}) return collection_${objProperty.javaName}"
        +"if (diff == null || modifiable.get()) {"
        +"collection_${objProperty.javaName}.setModificationUpdateAction(${objProperty.javaName}Updater)"
        +"} else {"
        +"collection_${objProperty.javaName}.cleanModificationUpdateAction()"
        +"}"
        +"return collection_${objProperty.javaName}"
      }
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"${objProperty.javaName}Updater.invoke(value)"
      }

    }

    // TODO: suspicious that code for List and Set is different
    is ValueType.Set<*> -> {
      val elementType = valueType.elementType
      if (valueType.isReferenceType()) {
        reportPropertyError("Set of references is not supported", objProperty)
        return
      }
      else {
        +"private val ${objProperty.javaName}Updater: (value: Set<${getJavaType(objProperty, elementType)}>) -> Unit = { value ->"
        +elementType.addVirtualFileIndex(objProperty)
        +"changedProperty.add(\"${objProperty.javaName}\")"
        +"}"
        +"override var ${objProperty.javaName}: MutableSet<${getJavaType(objProperty, elementType)}>"
        section("get()") {
          +"val collection_${objProperty.javaName} = getEntityData().${objProperty.javaName}"
          +"if (collection_${objProperty.javaName} !is ${MutableWorkspaceSet}) return collection_${objProperty.javaName}"
          +"if (diff == null || modifiable.get()) {"
          +"collection_${objProperty.javaName}.setModificationUpdateAction(${objProperty.javaName}Updater)"
          +"} else {"
          +"collection_${objProperty.javaName}.cleanModificationUpdateAction()"
          +"}"
          +"return collection_${objProperty.javaName}"
        }
        section("set(value)") {
          +"checkModificationAllowed()"
          +"getEntityData(true).${objProperty.javaName} = value"
          +"${objProperty.javaName}Updater.invoke(value)"
        }
      }
    }

    is ValueType.Map<*, *> -> {
      +"override var ${objProperty.javaName}: ${getJavaType(objProperty, valueType)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
      }
    }

    is ValueType.Optional<*> -> implWsBuilderBlockingCode(receiver, valueType.type, objProperty, "?")
    is ValueType.Structure<*> -> +"//TODO: ${objProperty.javaName}"
    is ValueType.JvmClass -> {
      +"override var ${objProperty.javaName}: ${getJavaType(objProperty, valueType).appendSuffix(optionalSuffix)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
        if (getJavaType(objProperty, valueType).decoded == VirtualFileUrl.decoded) {
          +"val _diff = diff"
          +"if (_diff != null) index(this, \"${objProperty.javaName}\", value)"
        }
      }
    }

    else -> {
      unsupportedTypeError(valueType, objProperty)
    }
  }
}

fun CodeContext.implWsBuilderIsInitializedCode(field: ObjProperty<*, *>) {
  val javaName = field.javaName
  val isChild = unwrapReferenceType(field.valueType)?.child
  when (field.valueType) {
    is ValueType.List<*> -> if (field.valueType.isReferenceType()) {
      if (isChild == null) {
        notReferenceError("isInitialized", field)
        return
      }
      lineComment("Check initialization for list with ref type")
      ifElse("_diff != null", {
        `if`("_diff.${Instrumentation.getManyChildrenBuilders}(${connectionIdForReference(field)}, this) == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] == null")
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }

    is ValueType.ObjRef<*> -> {
      if (isChild == null) {
        notReferenceError("isInitialized", field)
        return
      }
      ifElse("_diff != null", {
        `if`("_diff.${refsConnectionMethodCode(field, true)} == null") {
          line("error(\"Field ${field.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(field, "this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(field)})] == null")
      }.toString()
    }

    is ValueType.Int, is ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong,
      -> return
    else -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(field, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
  }
}

private fun CodeContext.isInitializedBaseCode(field: ObjProperty<*, *>, expression: String) {
  section("if ($expression)") {
    line("error(\"Field ${field.receiver.name}#${field.javaName} should be initialized\")")
  }
}

private fun ValueType<*>.addVirtualFileIndex(field: ObjProperty<*, *>): String {
  return when {
    this is ValueType.Blob && kotlinClassName == VirtualFileUrl.decoded ->
      """
        val _diff = diff
        if (_diff != null) index(this, "${field.javaName}", value)
        """.trimIndent()

    this is ValueType.JvmClass && kotlinClassName == LibraryRoot.decoded -> """
      val _diff = diff
      if (_diff != null) {
      indexLibraryRoots(value)
      }
      """.trimIndent()

    this is ValueType.JvmClass && javaClassName == SdkRoot.decoded -> """
      val _diff = diff
      if (_diff != null) {
      indexSdkRoots(value)
      }
      """.trimIndent()

    else -> ""
  }
}

fun CodeContext.suppressUncheckedCast() {
  annotation("Suppress(\"UNCHECKED_CAST\")")
}

private enum class ReferenceType {
  Child,
  Children,
  Parent,
  ParentOfMany
}

// TODO: assumption that `checkReference` was called previously
private fun CodeContext.entityReferencePropertyBuilderCode(
  receiver: ObjClass<*>,
  property: ObjProperty<*, *>,
  referencesInSymbolicId: Set<OwnProperty<*, *>>?
) {
  val usedInSymbolicId = referencesInSymbolicId?.contains(property) ?: false
  val connectionName = connectionIdForReference(property)
  val referencedEntityType = unwrapReferenceType(property.valueType) ?: run {
    reportPropertyError("entityReferencePropertyBuilderCode: null referencedEntityType", property)
    return
  }
  val backReference = run {
    val relevantReferences =
      referencedEntityType.target.refsFields + setOf(referencedEntityType.target.module, receiver.module).flatMap { it.extensions }
    relevantReferences.filter { referenceProperty ->
      val unwrapped = unwrapReferenceType(referenceProperty.valueType) ?: return@filter false
      unwrapped.target == property.receiver && referenceProperty.receiver == referencedEntityType.target && referenceProperty != property
    }.singleOrNull()
  } ?: run {
    reportPropertyError("entityReferencePropertyBuilderCode: null backReference", property)
    return
  }

  val referenceType = when {
    referencedEntityType.child && property.valueType is ValueType.List<*> -> ReferenceType.Children
    referencedEntityType.child && property.valueType is ValueType.Optional<*> -> ReferenceType.Child
    !referencedEntityType.child && backReference.valueType is ValueType.List<*> -> ReferenceType.ParentOfMany
    !referencedEntityType.child -> ReferenceType.Parent
    else -> {
      reportPropertyError("entityReferencePropertyBuilderCode", property)
      return
    }
  }
  
  val receiverName = property.receiver.name


  val referenceBuilderType = getJavaBuilderTypeWithGeneric(property)
  sectionNoBrackets("override var ${property.javaName}: $referenceBuilderType") {
    when (referenceType) {
      ReferenceType.Parent -> {
        +"get() = getParent($connectionName) as? $referenceBuilderType ?: error(\"${property.name} is null for $receiverName\")"
        section("set(value)") {
          +"changeParent(value, $connectionName)"
          +"changedProperty.add(\"${property.name}\")"
          if (usedInSymbolicId) +"updateSymbolicId(value, $connectionName)"
        }
      }
      ReferenceType.ParentOfMany -> {
        +"get() = getParent($connectionName) as? $referenceBuilderType ?: error(\"${property.name} is null for $receiverName\")"
        section("set(value)") {
          +"changeParentOfMany(value, $connectionName)"
          +"changedProperty.add(\"${property.name}\")"
          if (usedInSymbolicId) +"updateSymbolicId(value, $connectionName)"
        }
      }
      ReferenceType.Children -> {
        suppressUncheckedCast()
        +"get() = getChildren($connectionName) as $referenceBuilderType"
        section("set(value)") {
          +"changeChildren(value, $connectionName)"
          +"changedProperty.add(\"${property.name}\")"
        }
      }
      ReferenceType.Child -> {
        +"get() = getChild($connectionName) as? $referenceBuilderType"
        section("set(value)") {
          +"changeChild(value, $connectionName)"
          +"changedProperty.add(\"${property.name}\")"
        }
      }
    }
  }
}