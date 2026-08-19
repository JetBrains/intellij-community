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
import com.intellij.workspaceModel.codegen.impl.writer.SdkEntity
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.collectionProperties
import com.intellij.workspaceModel.codegen.impl.writer.extensions.compatibleJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.vfuFields
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.referencesInSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdIsInitializedCode
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdReferenceCode

fun CodeContext.entityBuilderImplementationCode(objClass: ObjClass<*>, hasConnections: Boolean) {
  section("internal class Builder(result: ${objClass.javaDataName}?): ${ModifiableWorkspaceEntityBase}<${objClass.javaFullName}, ${objClass.javaDataName}>(result), ${objClass.compatibleJavaBuilderName}") {
    +"internal constructor(): this(${objClass.javaDataName}())"

    section("override fun checkInitialization()") {
      line("val _diff = diff")
      listBuilder(getAllProperties(objClass, withSymbolicId = false, withOptional = false, withDefault = false)) { field ->
        implWsBuilderIsInitializedCode(field)
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
      listBuilder(getAllProperties(objClass, withSymbolicId = false, withRefs = false)) { field ->
        var type = field.valueType
        var qm = ""
        if (type is ValueType.Optional<*>) {
          qm = "?"
          type = type.type
        }
        when (type) {
          is ValueType.List<*> -> line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableList()")
          is ValueType.Set<*> -> line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableSet()")
          is ValueType.Map<*, *> -> line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableMap()")
          else -> line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource.${field.name}")
        }
      }

      line("updateChildToParentReferences(parents)")
    }

    val isIndexFunRequired =
      objClass.vfuFields.isNotEmpty() || objClass.name == LibraryEntity.simpleName || objClass.name == SdkEntity.simpleName
    if (isIndexFunRequired) {
      section("override fun index()") {
        for (vfuProperty in objClass.vfuFields) {
          val name = vfuProperty.name
          +"index(this, \"$name\", this.$name)"
        }
        if (objClass.name == LibraryEntity.simpleName) {
          +"indexLibraryRoots(roots)"
        }
        if (objClass.name == SdkEntity.simpleName) {
         +"indexSdkRoots(roots)"
        }
      }
    }

    if (objClass.name == LibraryEntity.simpleName) {
      section("private fun indexLibraryRoots(libraryRoots: List<LibraryRoot>)") {
        line("val jarDirectories = mutableSetOf<VirtualFileUrl>()")
        line("val libraryRootList = libraryRoots.map {")
        line("if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {")
        line("jarDirectories.add(it.url)")
        line("}")
        line("it.url")
        line("}.toHashSet()")
        line("index(this, \"roots\", libraryRootList)")
        line("indexJarDirectories(this, jarDirectories)")
      }
    }

    if (objClass.name == SdkEntity.simpleName) {
      section("private fun indexSdkRoots(sdkRoots: List<SdkRoot>)") {
        line("val sdkRootList = sdkRoots.map { it.url }.toHashSet()")
        line("index(this, \"roots\", sdkRootList)")
      }
    }

    val referencesInSymbolicId = referencesInSymbolicId(objClass)

    val propertiesToGenerate = getAllProperties(objClass, withSymbolicId = false)
    for (property in propertiesToGenerate) {
      getImplWsBuilderFieldCode(objClass, property, referencesInSymbolicId)
    }

    +"override fun getEntityClass(): Class<${objClass.javaFullName}> = ${objClass.javaFullName}::class.java"
    
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

