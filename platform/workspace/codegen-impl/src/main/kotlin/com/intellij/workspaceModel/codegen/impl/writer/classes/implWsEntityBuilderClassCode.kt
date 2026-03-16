// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsBuilderFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsBuilderIsInitializedCode

fun ObjClass<*>.implWsEntityBuilderCode(): String {
  return """
internal class Builder(result: $javaDataName?): ${ModifiableWorkspaceEntityBase}<$javaFullName, $javaDataName>(result), $compatibleJavaBuilderName {
internal constructor(): this($javaDataName())

${
    lines {
      section("override fun applyToBuilder(builder: ${MutableEntityStorage})") {
        `if`("this.diff != null") {
          ifElse("existsInBuilder(builder)", {
            line("this.diff = builder")
            line("return")
          }) {
            line("error(\"Entity $name is already created in a different builder\")")
          }
        }
        line("this.diff = builder")
        line("addToBuilder()")
        line("this.id = getEntityData().createEntityId()")
        lineComment("After adding entity data to the builder, we need to unbind it and move the control over entity data to builder")
        lineComment("Builder may switch to snapshot at any moment and lock entity data to modification")
        line("this.currentEntityData = null")
        list(vfuFields) {
          "index(this, \"$name\", this.$name)"
        }
        if (name == LibraryEntity.simpleName) {
          line("indexLibraryRoots(roots)")
        }
        if (name == SdkEntity.simpleName) {
          line("indexSdkRoots(roots)")
        }
        lineComment("Process linked entities that are connected without a builder")
        line("processLinkedEntities(builder)")

        //------------

        line("checkInitialization() // TODO uncomment and check failed tests")
      }
      result.append("\n")

      section("private fun checkInitialization()") {
        line("val _diff = diff")
        list(allFields.noSymbolicId().noOptional().noDefaultValue()) { lineBuilder, field ->
          lineBuilder.implWsBuilderIsInitializedCode(field)
        }
      }

      section("override fun connectionIdList(): List<${ConnectionId}>") { 
        line("return connections")
      }

      val collectionFields = allFields.noRefs().filter { it.valueType is ValueType.Collection<*,*> }
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
        line("dataSource as $javaFullName")
        list(allFields.noSymbolicId().noRefs()) { lineBuilder, field ->
          var type = field.valueType
          var qm = ""
          if (type is ValueType.Optional<*>) {
            qm = "?"
            type = type.type
          }
          when (type) {
            is ValueType.List<*> -> lineBuilder.line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableList()")
            is ValueType.Set<*> -> lineBuilder.line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableSet()")
            is ValueType.Map<*, *> -> lineBuilder.line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableMap()")
            else -> lineBuilder.line("if (this.${field.name} != dataSource${qm}.${field.name}) this.${field.name} = dataSource.${field.name}")
          }
        }

        line("updateChildToParentReferences(parents)")
      }

      if (name == LibraryEntity.simpleName) {
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

      if (name == SdkEntity.simpleName) {
        section("private fun indexSdkRoots(sdkRoots: List<SdkRoot>)") {
          line("val sdkRootList = sdkRoots.map { it.url }.toHashSet()")
          line("index(this, \"roots\", sdkRootList)")
        }
      }
    }
}
        
${allFields.filter { it.name != "symbolicId" }.lines { implWsBuilderFieldCode }.trimEnd()}

override fun getEntityClass(): Class<$javaFullName> = $javaFullName::class.java
}
"""
}

