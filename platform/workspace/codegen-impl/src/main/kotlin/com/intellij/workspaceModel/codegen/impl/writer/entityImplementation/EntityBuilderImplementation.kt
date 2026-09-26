// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.writer.ConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.LibraryEntity
import com.intellij.workspaceModel.codegen.impl.writer.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceList
import com.intellij.workspaceModel.codegen.impl.writer.MutableWorkspaceSet
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.collectionProperties
import com.intellij.workspaceModel.codegen.impl.writer.extensions.compatibleJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.referencesInSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdIsInitializedCode
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdReferenceCode
import com.intellij.workspaceModel.codegen.impl.writer.getToStringProperty
import com.intellij.workspaceModel.codegen.impl.writer.getVfuProperties

fun CodeContext.entityBuilderImplementationCode(objClass: ObjClass<*>, hasConnections: Boolean) {
  section("internal class Builder(result: ${objClass.javaDataName}?): ${ModifiableWorkspaceEntityBase}<${objClass.javaFullName}, ${objClass.javaDataName}>(result), ${objClass.compatibleJavaBuilderName}") {
    +"internal constructor(): this(${objClass.javaDataName}())"

    section("override fun checkInitialization()") {
      line("val _diff = diff")
      val properties = getAllProperties(objClass, withSymbolicId = false, withOptional = false, withDefault = false)
      for (property in properties) {
        implWsBuilderIsInitializedCode(property)
      }
      symbolicIdIsInitializedCode(objClass)
    }

    section("override fun connectionIdList(): List<${ConnectionId}>") {
      if (hasConnections) +"return connections"
      else +"return emptyList()"
    }

    val collectionFields = collectionProperties(objClass)
    if (collectionFields.isNotEmpty()) {
      section("override fun afterModification()") {
        collectionFields.forEach { field ->
          line("val collection_${field.javaName} = getEntityData().${field.javaName}")
          if (field.valueType is ValueType.List<*>) {
            `if`("collection_${field.javaName} is ${MutableWorkspaceList}<*>") {
              line("collection_${field.javaName}.cleanModificationUpdateAction()")
            }
          }
          if (field.valueType is ValueType.Set<*>) {
            `if`("collection_${field.javaName} is ${MutableWorkspaceSet}<*>") {
              line("collection_${field.javaName}.cleanModificationUpdateAction()")
            }
          }
        }
      }
    }

    lineComment("Relabeling code, move information from dataSource to this builder")
    section("override fun relabel(dataSource: ${WorkspaceEntity}, parents: Set<${WorkspaceEntity}>?)") {
      line("dataSource as ${objClass.javaFullName}")
      val properties = getAllProperties(objClass, withSymbolicId = false, withRefs = false)
      for (property in properties) { 
        val valueTypeRaw = property.valueType
        val (valueType, safeCall) = if (valueTypeRaw is ValueType.Optional<*>) {
          valueTypeRaw.type to "?"
        } else {
          valueTypeRaw to ""
        }
        when (valueType) {
          is ValueType.List<*> -> line("if (this.${property.name} != dataSource.${property.name}) this.${property.name} = dataSource.${property.name}${safeCall}.toMutableList()")
          is ValueType.Set<*> -> line("if (this.${property.name} != dataSource.${property.name}) this.${property.name} = dataSource.${property.name}${safeCall}.toMutableSet()")
          is ValueType.Map<*, *> -> line("if (this.${property.name} != dataSource.${property.name}) this.${property.name} = dataSource.${property.name}${safeCall}.toMutableMap()")
          else -> line("if (this.${property.name} != dataSource.${property.name}) this.${property.name} = dataSource.${property.name}")
        }
      }

      line("updateChildToParentReferences(parents)")
    }

    val vfuProperties = getVfuProperties(objClass)
    if (vfuProperties.isNotEmpty() || objClass.name == LibraryEntity.simpleName) {
      section("override fun index()") {
        for (vfuList in vfuProperties.values) {
          for (vfu in vfuList) {
            +"index(this, ${vfu.quotedName}, ${vfu.indexAccess})"
          }
        }
        if (objClass.name == LibraryEntity.simpleName) {
          +"val jarDirectories = roots.filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }.toHashSet()"
          +"indexJarDirectories(this, jarDirectories)"
        }
      }
    }

    //if (objClass.name == SdkEntity.simpleName) {
    //  section("private fun indexSdkRoots(sdkRoots: List<SdkRoot>)") {
    //    line("val sdkRootList = sdkRoots.map { it.url }.toHashSet()")
    //    line("index(this, \"roots\", sdkRootList)")
    //  }
    //}

    val referencesInSymbolicId = referencesInSymbolicId(objClass)

    val propertiesToGenerate = getAllProperties(objClass, withSymbolicId = false)
    for (property in propertiesToGenerate) {
      generateBuilderPropertyCode(objClass, property, referencesInSymbolicId, vfuProperties)
    }

    +"override fun getEntityClass(): Class<${objClass.javaFullName}> = ${objClass.javaFullName}::class.java"

    customBuilderToString(objClass)
    
    if (!referencesInSymbolicId.isNullOrEmpty()) {
      section("override fun updateSymbolicId(parent: WorkspaceEntityBuilder<*>, connectionId: ConnectionId)") {
        for (reference in referencesInSymbolicId) {
          val connectionName = connectionIdForReference(reference)
          `if`("connectionId == $connectionName") {
            symbolicIdReferenceCode(referencesInSymbolicId, reference)
          }
        }
      }
    }
  }
}

private fun CodeContext.customBuilderToString(objClass: ObjClass<*>) {
  val toStringProperty = getToStringProperty(objClass) ?: return
  +"override fun toString()=${toStringProperty.expression}"
}

