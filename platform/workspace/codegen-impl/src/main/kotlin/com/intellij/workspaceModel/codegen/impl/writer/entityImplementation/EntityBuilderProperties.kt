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
import com.intellij.workspaceModel.codegen.impl.writer.LibraryRoot
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceList
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceSet
import com.intellij.workspaceModel.codegen.impl.writer.VfuProperties
import com.intellij.workspaceModel.codegen.impl.writer.VfuProperty
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType

internal fun CodeContext.generateBuilderPropertyCode(
  receiver: ObjClass<*>,
  property: ObjProperty<*, *>,
  referencesInSymbolicId: Set<OwnProperty<*, *>>?,
  vfuProperties: VfuProperties,
) {
  if (property.valueType.isReferenceType()) {
    builderReferenceProperty(receiver, property, referencesInSymbolicId)
  }
  else {
    builderProperty(receiver, property.valueType, property, vfuProperties)
  }
}

private fun CodeContext.unexpectedReference(objProperty: ObjProperty<*, *>) {
  reportPropertyError("Unexpected reference while building regular property code", objProperty)
}

private fun CodeContext.builderProperty(
  receiver: ObjClass<*>,
  valueType: ValueType<*>,
  objProperty: ObjProperty<*, *>,
  vfuProperties: VfuProperties,
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
      val vfuList = vfuProperties[objProperty.name]
      if (vfuList != null) virtualFileIndexUpdater(objProperty, vfuList)
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
        val vfuList = vfuProperties[objProperty.name]
        if (vfuList != null) virtualFileIndexUpdater(objProperty, vfuList)
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

    is ValueType.Optional<*> -> builderProperty(receiver, valueType.type, objProperty, vfuProperties, "?")
    is ValueType.Structure<*> -> +"//TODO: ${objProperty.javaName}"
    is ValueType.JvmClass -> {
      +"override var ${objProperty.javaName}: ${getJavaType(objProperty, valueType).appendSuffix(optionalSuffix)}"
      +"get() = getEntityData().${objProperty.javaName}"
      section("set(value)") {
        +"checkModificationAllowed()"
        +"getEntityData(true).${objProperty.javaName} = value"
        +"changedProperty.add(\"${objProperty.javaName}\")"
        val vfuList = vfuProperties[objProperty.name]
        if (vfuList != null) {
          +"val _diff = diff"
          +"if (_diff != null) {"
          for (vfu in vfuList) {
            +"index(this, ${vfu.quotedName}, ${vfu.setterAccess})"
          }
          +"}"
        }
      }
    }

    else -> {
      unsupportedTypeError(valueType, objProperty)
    }
  }
}

fun CodeContext.implWsBuilderIsInitializedCode(property: ObjProperty<*, *>) {
  val javaName = property.javaName
  val isChild = unwrapReferenceType(property.valueType)?.child
  when (property.valueType) {
    is ValueType.List<*> -> if (property.valueType.isReferenceType()) {
      if (isChild == null) {
        notReferenceError("isInitialized", property)
        return
      }
    }
    else {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(property, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }

    is ValueType.ObjRef<*> -> {
      if (isChild == null) {
        notReferenceError("isInitialized", property)
        return
      }
      ifElse("_diff != null", {
        `if`("_diff.${refsConnectionMethodCode(property, true)} == null") {
          line("error(\"Field ${property.receiver.name}#$javaName should be initialized\")")
        }
      }) {
        isInitializedBaseCode(property, "this.entityLinks[${EntityLink}($isChild, ${connectionIdForReference(property)})] == null")
      }.toString()
    }

    is ValueType.Int, is ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong,
      -> return
    else -> {
      val capitalizedFieldName = javaName.replaceFirstChar { it.titlecaseChar() }
      isInitializedBaseCode(property, "!getEntityData().is${capitalizedFieldName}Initialized()")
    }
  }
}

private fun CodeContext.isInitializedBaseCode(field: ObjProperty<*, *>, expression: String) {
  section("if ($expression)") {
    line("error(\"Field ${field.receiver.name}#${field.javaName} should be initialized\")")
  }
}

private fun CodeContext.virtualFileIndexUpdater(property: ObjProperty<*, *>, vfuList: List<VfuProperty>) {
  val propertyValueType = property.valueType
  val isLibraryRootList = propertyValueType is ValueType.List<*> && (propertyValueType.elementType as? ValueType.JvmClass)?.kotlinClassName == LibraryRoot.decoded
  
  +"if (diff != null) {"
  for (vfu in vfuList) {
    +"index(this, ${vfu.quotedName}, ${vfu.setterAccess})"
  }
  if (isLibraryRootList) {
    +"val jarDirectories = value.filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }.toHashSet()"
    +"indexJarDirectories(this, jarDirectories)"
  }
  +"}"
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
private fun CodeContext.builderReferenceProperty(
  receiver: ObjClass<*>,
  property: ObjProperty<*, *>,
  referencesInSymbolicId: Set<OwnProperty<*, *>>?,
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


  val referenceIsAbstract = unwrapReferenceType(property.valueType)?.target?.openness == ObjClass.Openness.abstract
  val referenceBuilderType = getJavaBuilderTypeWithGeneric(property)
  sectionNoBrackets("override var ${property.javaName}: $referenceBuilderType") {
    if (referenceIsAbstract) suppressUncheckedCast()
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
        if (!referenceIsAbstract) suppressUncheckedCast()
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