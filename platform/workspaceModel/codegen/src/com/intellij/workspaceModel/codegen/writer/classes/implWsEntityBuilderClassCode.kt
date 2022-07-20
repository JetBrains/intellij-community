package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.TCollection
import com.intellij.workspaceModel.codegen.fields.implWsBuilderFieldCode
import com.intellij.workspaceModel.codegen.fields.implWsBuilderIsInitializedCode
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase

fun ObjType<*, *>.implWsEntityBuilderCode(): String {
  return """
    class Builder(val result: $javaDataName?): ${ModifiableWorkspaceEntityBase::class.fqn}<$javaFullName>(), $javaBuilderName {
        constructor(): this($javaDataName())
        
${
    lines("        ") {
      section("override fun applyToBuilder(builder: ${MutableEntityStorage::class.fqn})") {
        `if`("this.diff != null") {
          ifElse("existsInBuilder(builder)", {
            line("this.diff = builder")
            line("return")
          }) {
            line("error(\"Entity $javaFullName is already created in a different builder\")")
          }
        }
        line()
        line("this.diff = builder")
        line("this.snapshot = builder")
        line("addToBuilder()")
        line("this.id = getEntityData().createEntityId()")
        line()
        list(structure.vfuFields) {
          val suffix = if (type is TCollection<*, *>) ".toHashSet()" else ""
          "index(this, \"$javaName\", this.$javaName$suffix)"
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
        list(structure.allFields.noPersistentId().noOptional().noDefaultValue()) { lineBuilder, field ->
          lineBuilder.implWsBuilderIsInitializedCode(field)
        }
      }

      line()
      section("override fun connectionIdList(): List<${ConnectionId::class.fqn}>") { 
        line("return connections")
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
        
        ${structure.allFields.filter { it.name != "persistentId" }.lines("        ") { implWsBuilderFieldCode }.trimEnd()}
        
        override fun getEntityData(): $javaDataName${if (abstract) "<T>" else ""} = result ?: super.getEntityData() as $javaDataName${if (abstract) "<T>" else ""}
        override fun getEntityClass(): Class<$javaFullName> = $javaFullName::class.java
    }
    """.trimIndent()
}

