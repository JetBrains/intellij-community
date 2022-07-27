// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.deft.meta.impl.KtInterfaceType
import com.intellij.workspaceModel.codegen.fields.implWsDataFieldCode
import com.intellij.workspaceModel.codegen.fields.implWsDataFieldInitializedCode
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.fqn7
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.writer.allFields
import com.intellij.workspaceModel.codegen.writer.hasSetter
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet

/**
 * - Soft links
 * - with PersistentId
 */

val ObjClass<*>.javaDataName
  get() = "${name.replace(".", "")}Data"

val ObjClass<*>.isEntityWithPersistentId: Boolean
  get() = superTypes.any { 
    it is KtInterfaceType && it.shortName == WorkspaceEntityWithPersistentId::class.java.simpleName
    || it is ObjClass<*> && (it.javaFullName.decoded == WorkspaceEntityWithPersistentId::class.java.name || it.isEntityWithPersistentId)
  }

fun ObjClass<*>.implWsDataClassCode(): String {
  val entityDataBaseClass = if (isEntityWithPersistentId) {
    "${WorkspaceEntityData::class.fqn}.WithCalculablePersistentId<$javaFullName>()"
  }
  else {
    "${WorkspaceEntityData::class.fqn}<$javaFullName>()"
  }
  val hasSoftLinks = hasSoftLinks()
  val softLinkable = if (hasSoftLinks) SoftLinkable::class.fqn else null
  return lines {
    section("class $javaDataName : ${sups(entityDataBaseClass, softLinkable?.encodedString)}") label@{
      listNl(allFields.noRefs().noEntitySource().noPersistentId()) { implWsDataFieldCode }

      listNl(allFields.noRefs().noEntitySource().noPersistentId().noOptional().noDefaultValue()) { implWsDataFieldInitializedCode }

      this@implWsDataClassCode.softLinksCode(this, hasSoftLinks)

      sectionNl(
        "override fun wrapAsModifiable(diff: ${MutableEntityStorage::class.fqn}): ${ModifiableWorkspaceEntity::class.fqn}<$javaFullName>") {
        line("val modifiable = $javaImplBuilderName(null)")
        line("modifiable.allowModifications {")
        line("  modifiable.diff = diff")
        line("  modifiable.snapshot = diff")
        line("  modifiable.id = createEntityId()")
        line("  modifiable.entitySource = this.entitySource")
        line("}")
        line("modifiable.changedProperty.clear()")
        line("return modifiable")
      }

      // --- createEntity
      sectionNl("override fun createEntity(snapshot: ${EntityStorage::class.fqn}): $javaFullName") {
        line("val entity = $javaImplName()")
        list(allFields.noRefs().noEntitySource().noPersistentId()) {
          if (hasSetter) {
            if (this.valueType is ValueType.Set<*> && !this.valueType.isRefType()) {
              "entity.$implFieldName = $name.toSet()"
            } else if (this.valueType is ValueType.List<*> && !this.valueType.isRefType()) {
              "entity.$implFieldName = $name.toList()"
            } else {
              "entity.$implFieldName = $name"
            }
          } else {
            "entity.$name = $name"
          }
        }
        line("entity.entitySource = entitySource")
        line("entity.snapshot = snapshot")
        line("entity.id = createEntityId()")
        line("return entity")
      }

      val collectionFields = allFields.noRefs().filter { it.valueType is ValueType.Collection<*, *> }
      if (collectionFields.isNotEmpty()) {
        sectionNl("override fun clone(): $javaDataName") {
          val fieldName = "clonedEntity"
          line("val $fieldName = super.clone()")
          line("$fieldName as $javaDataName")
          collectionFields.forEach { field ->
            if (field.valueType is ValueType.Set<*>) {
              line("$fieldName.${field.name} = $fieldName.${field.name}.${fqn7(Collection<*>::toMutableWorkspaceSet)}()")
            } else {
              line("$fieldName.${field.name} = $fieldName.${field.name}.${fqn7(Collection<*>::toMutableWorkspaceList)}()")
            }
          }
          line("return $fieldName")
        }
      }

      if (isEntityWithPersistentId) {
        val persistentIdField = fields.first { it.name == "persistentId" }
        val valueKind = persistentIdField.valueKind
        val methodBody = (valueKind as ObjProperty.ValueKind.Computable).expression
        if (methodBody.contains("return")) {
          if (methodBody.startsWith("{")) {
            line("override fun persistentId(): ${PersistentEntityId::class.fqn}<*> $methodBody \n")
          } else {
            sectionNl("override fun persistentId(): ${PersistentEntityId::class.fqn}<*>") {
                line(methodBody)
            }
          }
        } else {
          sectionNl("override fun persistentId(): ${PersistentEntityId::class.fqn}<*>") {
            if (methodBody.startsWith("=")) {
              line("return ${methodBody.substring(2)}")
            }
            else {
              line("return $methodBody")
            }
          }
        }
      }

      // --- getEntityInterface method
      sectionNl("override fun getEntityInterface(): Class<out ${WorkspaceEntity::class.fqn}>") {
        line("return $name::class.java")
      }

      sectionNl("override fun serialize(ser: ${EntityInformation.Serializer::class.fqn})") {
        //InterfaceTraverser(simpleTypes).traverse(this@implWsDataClassCode, SerializatorVisitor(this@sectionNl))
      }

      sectionNl("override fun deserialize(de: ${EntityInformation.Deserializer::class.fqn})") {
        //InterfaceTraverser(simpleTypes).traverse(this@implWsDataClassCode, DeserializationVisitor(this@sectionNl))
      }

      // --- equals
      sectionNl("override fun equals(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this::class != other::class) return false")

        lineWrapped("other as $javaDataName")

        list(allFields.noRefs().noPersistentId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- equalsIgnoringEntitySource
      sectionNl("override fun equalsIgnoringEntitySource(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this::class != other::class) return false")

        lineWrapped("other as $javaDataName")

        list(allFields.noRefs().noEntitySource().noPersistentId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- hashCode
      section("override fun hashCode(): Int") {
        line("var result = entitySource.hashCode()")
        list(allFields.noRefs().noEntitySource().noPersistentId()) {
          "result = 31 * result + $name.hashCode()"
        }
        line("return result")
      }
    }
  }
}

fun List<ObjProperty<*, *>>.noRefs(): List<ObjProperty<*, *>> = this.filterNot { it.valueType.isRefType() }
fun List<ObjProperty<*, *>>.noEntitySource() = this.filter { it.name != "entitySource" }
fun List<ObjProperty<*, *>>.noPersistentId() = this.filter { it.name != "persistentId" }
fun List<ObjProperty<*, *>>.noOptional() = this.filter { it.valueType !is com.intellij.workspaceModel.codegen.deft.meta.ValueType.Optional<*> }
fun List<ObjProperty<*, *>>.noDefaultValue() = this.filter { it.valueKind == ObjProperty.ValueKind.Plain }
