package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.fields.implWsBuilderFieldCode
import com.intellij.workspaceModel.codegen.fields.implWsBuilderIsInitializedCode
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.writer.allFields
import com.intellij.workspaceModel.codegen.writer.javaName
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceSet

fun ObjClass<*>.implWsEntityBuilderCode(): String {
  return """
    class Builder(result: $javaDataName?): ${ModifiableWorkspaceEntityBase::class.fqn}<$javaFullName, $javaDataName>(result), $javaBuilderName {
        constructor(): this($javaDataName())
        
${
    lines(2) {
      section("override fun applyToBuilder(builder: ${MutableEntityStorage::class.fqn})") {
        `if`("this.diff != null") {
          ifElse("existsInBuilder(builder)", {
            line("this.diff = builder")
            line("return")
          }) {
            line("error(\"Entity $name is already created in a different builder\")")
          }
        }
        line()
        line("this.diff = builder")
        line("this.snapshot = builder")
        line("addToBuilder()")
        line("this.id = getEntityData().createEntityId()")
        lineComment("After adding entity data to the builder, we need to unbind it and move the control over entity data to builder")
        lineComment("Builder may switch to snapshot at any moment and lock entity data to modification")
        line("this.currentEntityData = null")
        line()
        list(vfuFields) {
          val suffix = if (valueType is ValueType.Collection<*, *>) ".toHashSet()" else ""
          "index(this, \"$name\", this.$name$suffix)"
        }
        if (name == LibraryEntity::class.simpleName) {
          line("indexLibraryRoots(${LibraryEntity::roots.name})")
        }
        lineComment("Process linked entities that are connected without a builder")
        line("processLinkedEntities(builder)")

        //------------

        line("checkInitialization() // TODO uncomment and check failed tests")
      }
      result.append("\n")

      section("fun checkInitialization()") {
        line("val _diff = diff")
        list(allFields.noSymbolicId().noOptional().noDefaultValue()) { lineBuilder, field ->
          lineBuilder.implWsBuilderIsInitializedCode(field)
        }
      }

      line()
      section("override fun connectionIdList(): List<${ConnectionId::class.fqn}>") { 
        line("return connections")
      }
      line()

      val collectionFields = allFields.noRefs().filter { it.valueType is ValueType.Collection<*,*> }
      if (collectionFields.isNotEmpty()) {
        section("override fun afterModification()") {
          collectionFields.forEach { field ->
            line("val collection_${field.javaName} = getEntityData().${field.javaName}")
            if (field.valueType is ValueType.List<*>) {
              `if`("collection_${field.javaName} is ${MutableWorkspaceList::class.fqn}<*>") {
                line("collection_${field.javaName}.cleanModificationUpdateAction()")
              }
            }
            if (field.valueType is ValueType.Set<*>) {
              `if`("collection_${field.javaName} is ${MutableWorkspaceSet::class.fqn}<*>") {
              line("collection_${field.javaName}.cleanModificationUpdateAction()")
              }
            }
          }
        }
        line()
      }

      lineComment("Relabeling code, move information from dataSource to this builder")
      section("override fun relabel(dataSource: ${WorkspaceEntity::class.fqn}, parents: Set<WorkspaceEntity>?)") {
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

        `if`("parents != null") {
          allRefsFields.filterNot { it.valueType.getRefType().child }.forEach {
            val parentType = it.valueType
            if (parentType is ValueType.Optional) {
              line("val ${it.name}New = parents.filterIsInstance<${parentType.javaType}>().singleOrNull()")
              `if`("(${it.name}New == null && this.${it.name} != null) || (${it.name}New != null && this.${it.name} == null) || (${it.name}New != null && this.${it.name} != null && (this.${it.name} as WorkspaceEntityBase).id != (${it.name}New as WorkspaceEntityBase).id)") {
                line("this.${it.name} = ${it.name}New")
              }
            } else {
              line("val ${it.name}New = parents.filterIsInstance<${parentType.javaType}>().single()")
              `if`("(this.${it.name} as WorkspaceEntityBase).id != (${it.name}New as WorkspaceEntityBase).id") {
                line("this.${it.name} = ${it.name}New")
              }
            }
          }
        }
      }

      if (name == LibraryEntity::class.simpleName) {
        section("private fun indexLibraryRoots(libraryRoots: List<LibraryRoot>)") {
          line("val jarDirectories = mutableSetOf<VirtualFileUrl>()")
          line("val libraryRootList = libraryRoots.map {")
          line("  if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {")
          line("    jarDirectories.add(it.url)")
          line("  }")
          line("  it.url")
          line("}.toHashSet()")
          line("index(this, \"roots\", libraryRootList)")
          line("indexJarDirectories(this, jarDirectories)")
        }
      }
    }
  }
        
        ${allFields.filter { it.name != "symbolicId" }.lines("        ") { implWsBuilderFieldCode }.trimEnd()}
        
        override fun getEntityClass(): Class<$javaFullName> = $javaFullName::class.java
    }
    """.trimIndent()
}

