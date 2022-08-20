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
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase

fun ObjClass<*>.implWsEntityBuilderCode(): String {
  return """
    class Builder(val result: $javaDataName?): ${ModifiableWorkspaceEntityBase::class.fqn}<$javaFullName>(), $javaBuilderName {
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
        list(allFields.noPersistentId().noOptional().noDefaultValue()) { lineBuilder, field ->
          lineBuilder.implWsBuilderIsInitializedCode(field)
        }
      }

      line()
      section("override fun connectionIdList(): List<${ConnectionId::class.fqn}>") { 
        line("return connections")
      }
      
      line()
      lineComment("Relabeling code, move information from dataSource to this builder")
      section("override fun relabel(dataSource: ${WorkspaceEntity::class.fqn}, parents: Set<WorkspaceEntity>?)") {
        line("dataSource as $javaFullName")
        list(allFields.noPersistentId().noRefs()) { lineBuilder, field ->
          var type = field.valueType
          var qm = ""
          if (type is ValueType.Optional<*>) {
            qm = "?"
            type = type.type
          }
          when (type) {
            is ValueType.List<*> -> lineBuilder.line("this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableList()")
            is ValueType.Set<*> -> lineBuilder.line("this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableSet()")
            is ValueType.Map<*, *> -> lineBuilder.line("this.${field.name} = dataSource${qm}.${field.name}${qm}.toMutableMap()")
            else -> lineBuilder.line("this.${field.name} = dataSource.${field.name}")
          }
        }

        `if`("parents != null") {
          allRefsFields.filterNot { it.valueType.getRefType().child }.forEach {
            val parentType = it.valueType
            if (parentType is ValueType.Optional) {
              line("this.${it.name} = parents.filterIsInstance<${parentType.type.javaType}>().singleOrNull()")
            } else {
              line("this.${it.name} = parents.filterIsInstance<${parentType.javaType}>().single()")
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
        
        ${allFields.filter { it.name != "persistentId" }.lines("        ") { implWsBuilderFieldCode }.trimEnd()}
        
        override fun getEntityData(): $javaDataName${if (openness.extendable) "<T>" else ""} = result ?: super.getEntityData() as $javaDataName${if (openness.extendable) "<T>" else ""}
        override fun getEntityClass(): Class<$javaFullName> = $javaFullName::class.java
    }
    """.trimIndent()
}

