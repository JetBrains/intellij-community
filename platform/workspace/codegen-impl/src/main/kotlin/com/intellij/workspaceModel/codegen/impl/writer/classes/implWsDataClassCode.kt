// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.metadata.fullName
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsDataFieldCode

/**
 * - Soft links
 * - with SymbolicId
 */

val ObjClass<*>.javaDataName
  get() = "${name.replace(".", "")}Data"

val ObjClass<*>.isEntityWithSymbolicId: Boolean
  get() = superTypes.any {
    it is ObjClass<*> && (it.javaFullName.decoded == WorkspaceEntityWithSymbolicId.decoded || it.isEntityWithSymbolicId)
  }

fun ObjClass<*>.implWsDataClassCode(): String {
  val entityDataBaseClass = "${WorkspaceEntityData}<$javaFullName>()"
  val hasSoftLinks = hasSoftLinks()
  val softLinkable = if (hasSoftLinks) SoftLinkable else null
  return lines {
    line("@OptIn($WorkspaceEntityInternalApi::class)")
    section("internal class $javaDataName : ${sups(entityDataBaseClass, softLinkable?.encodedString)}") label@{

      listNl(allFields.noRefs().noEntitySource().noSymbolicId()) { implWsDataFieldCode }

      listNl(allFields.noRefs().noEntitySource().noSymbolicId().noOptional().noDefaultValue()) { implWsDataFieldInitializedCode }

      this@implWsDataClassCode.softLinksCode(this, hasSoftLinks)

      sectionNl(
        "override fun wrapAsModifiable(diff: ${MutableEntityStorage}): ${WorkspaceEntity.Builder}<$javaFullName>") {
        line("val modifiable = $javaImplBuilderName(null)")
        line("modifiable.diff = diff")
        line("modifiable.id = createEntityId()")
        line("return modifiable")
      }

      // --- createEntity
      line("@OptIn($EntityStorageInstrumentationApi::class)")
      sectionNl("override fun createEntity(snapshot: $EntityStorageInstrumentation): $javaFullName") {
        line("val entityId = createEntityId()")
        section("return snapshot.initializeEntity(entityId)") {
          line("val entity = $javaImplName(this)")
          line("entity.snapshot = snapshot")
          line("entity.id = entityId")
          line("entity")
        }
      }

      sectionNl("override fun getMetadata(): $EntityMetadata") {
        line("return ${MetadataStorage.IMPL_NAME}.${MetadataStorage.getMetadataByTypeFqn}($fullName) as $EntityMetadata")
      }

      val collectionFields = allFields.noRefs().filter { it.valueType is ValueType.Collection<*, *> }
      if (collectionFields.isNotEmpty()) {
        sectionNl("override fun clone(): $javaDataName") {
          val fieldName = "clonedEntity"
          line("val $fieldName = super.clone()")
          line("$fieldName as $javaDataName")
          collectionFields.forEach { field ->
            if (field.valueType is ValueType.Set<*>) {
              line("$fieldName.${field.name} = $fieldName.${field.name}.${StorageCollection.toMutableWorkspaceSet}()")
            } else {
              line("$fieldName.${field.name} = $fieldName.${field.name}.${StorageCollection.toMutableWorkspaceList}()")
            }
          }
          line("return $fieldName")
        }
      }

      // --- getEntityInterface method
      sectionNl("override fun getEntityInterface(): Class<out ${WorkspaceEntity}>") {
        line("return $name::class.java")
      }

      sectionNl("override fun createDetachedEntity(parents: List<${WorkspaceEntity.Builder}<*>>): ${WorkspaceEntity.Builder}<*>") {
        val noRefs = allFields.noRefs().noSymbolicId()
        val mandatoryFields = allFields.mandatoryFields()
        val constructor = mandatoryFields.joinToString(", ") { it.name }.let { if (it.isNotBlank()) "($it)" else "" }
        val optionalFields = noRefs.filterNot { it in mandatoryFields }

        section("return $javaFullName$constructor") {
          optionalFields.forEach {
            line("this.${it.name} = this@$javaDataName.${it.name}")
          }
          allRefsFields.filterNot { it.valueType.getRefType().child }.forEach {
            val parentType = it.valueType
            if (parentType is ValueType.Optional) {
              line("this.${it.name} = parents.filterIsInstance<${parentType.type.javaBuilderTypeWithGeneric}>().singleOrNull()")
            } else {
              line("parents.filterIsInstance<${parentType.javaBuilderTypeWithGeneric}>().singleOrNull()?.let { this.${it.name} = it }")
            }
          }
        }
      }

      sectionNl("override fun getRequiredParents(): List<Class<out WorkspaceEntity>>") {
        line("val res = mutableListOf<Class<out WorkspaceEntity>>()")
        allRefsFields.filterNot { it.valueType.getRefType().child }.forEach {
          val parentType = it.valueType
          if (parentType !is ValueType.Optional) {
            line("res.add(${parentType.javaType}::class.java)")
          }
        }
        line("return res")
      }

      // --- equals
      val keyFields = allFields.filter { it.isKey }
      sectionNl("override fun equals(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this.javaClass != other.javaClass) return false")

        lineWrapped("other as $javaDataName")

        list(allFields.noRefs().noSymbolicId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- equalsIgnoringEntitySource
      sectionNl("override fun equalsIgnoringEntitySource(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this.javaClass != other.javaClass) return false")

        lineWrapped("other as $javaDataName")

        list(allFields.noRefs().noEntitySource().noSymbolicId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- hashCode
      section("override fun hashCode(): Int") {
        line("var result = entitySource.hashCode()")
        list(allFields.noRefs().noEntitySource().noSymbolicId()) {
          "result = 31 * result + $name.hashCode()"
        }
        line("return result")
      }

      // --- hashCodeIgnoringEntitySource
      section("override fun hashCodeIgnoringEntitySource(): Int") {
        line("var result = javaClass.hashCode()")
        list(allFields.noRefs().noEntitySource().noSymbolicId()) {
          "result = 31 * result + $name.hashCode()"
        }
        line("return result")
      }

      if (keyFields.isNotEmpty()) {
        line()
        section("override fun equalsByKey(other: Any?): Boolean") {
          line("if (other == null) return false")
          line("if (this.javaClass != other.javaClass) return false")

          lineWrapped("other as $javaDataName")

          list(keyFields) {
            "if (this.$name != other.$name) return false"
          }

          line("return true")
        }
        line()
        section("override fun hashCodeByKey(): Int") {
          line("var result = javaClass.hashCode()")
          list(keyFields) {
            "result = 31 * result + $name.hashCode()"
          }
          line("return result")
        }
      }
    }
  }
}

fun List<ObjProperty<*, *>>.noRefs(): List<ObjProperty<*, *>> = this.filterNot { it.valueType.isRefType() }
fun List<ObjProperty<*, *>>.noEntitySource() = this.filter { it.name != "entitySource" }
fun List<ObjProperty<*, *>>.noSymbolicId() = this.filter { it.name != "symbolicId" }
fun List<ObjProperty<*, *>>.noOptional() = this.filter { it.valueType !is ValueType.Optional<*> }
fun List<ObjProperty<*, *>>.noDefaultValue() = this.filter { it.valueKind == ObjProperty.ValueKind.Plain }