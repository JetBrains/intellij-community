// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsDataFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsDataFieldInitializedCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.allFields
import com.intellij.workspaceModel.codegen.impl.writer.EntityStorage
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity

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
  val entityDataBaseClass = if (isEntityWithSymbolicId) {
    "${WorkspaceEntityData}.WithCalculableSymbolicId<$javaFullName>()"
  }
  else {
    "${WorkspaceEntityData}<$javaFullName>()"
  }
  val hasSoftLinks = hasSoftLinks()
  val softLinkable = if (hasSoftLinks) SoftLinkable else null
  return lines {
    section("class $javaDataName : ${sups(entityDataBaseClass, softLinkable?.encodedString)}") label@{
      listNl(allFields.noRefs().noEntitySource().noSymbolicId()) { implWsDataFieldCode }

      listNl(allFields.noRefs().noEntitySource().noSymbolicId().noOptional().noDefaultValue()) { implWsDataFieldInitializedCode }

      this@implWsDataClassCode.softLinksCode(this, hasSoftLinks)

      sectionNl(
        "override fun wrapAsModifiable(diff: ${MutableEntityStorage}): ${WorkspaceEntity.Builder}<$javaFullName>") {
        line("val modifiable = $javaImplBuilderName(null)")
        line("modifiable.diff = diff")
        line("modifiable.snapshot = diff")
        line("modifiable.id = createEntityId()")
        line("return modifiable")
      }

      // --- createEntity
      sectionNl("override fun createEntity(snapshot: $EntityStorage): $javaFullName") {
        section("return getCached(snapshot)") {
          line("val entity = $javaImplName(this)")
          line("entity.snapshot = snapshot")
          line("entity.id = createEntityId()")
          line("entity")
        }
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

      if (isEntityWithSymbolicId) {
        val symbolicIdField = fields.first { it.name == "symbolicId" }
        val valueKind = symbolicIdField.valueKind
        val methodBody = (valueKind as ObjProperty.ValueKind.Computable).expression
        if (methodBody.contains("return")) {
          if (methodBody.startsWith("{")) {
            line("override fun symbolicId(): ${SymbolicEntityId}<*> $methodBody \n")
          } else {
            sectionNl("override fun symbolicId(): ${SymbolicEntityId}<*>") {
                line(methodBody)
            }
          }
        } else {
          sectionNl("override fun symbolicId(): ${SymbolicEntityId}<*>") {
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
      sectionNl("override fun getEntityInterface(): Class<out ${WorkspaceEntity}>") {
        line("return $name::class.java")
      }

      sectionNl("override fun serialize(ser: ${EntityInformation.Serializer})") {
        //InterfaceTraverser(simpleTypes).traverse(this@implWsDataClassCode, SerializatorVisitor(this@sectionNl))
      }

      sectionNl("override fun deserialize(de: ${EntityInformation.Deserializer})") {
        //InterfaceTraverser(simpleTypes).traverse(this@implWsDataClassCode, DeserializationVisitor(this@sectionNl))
      }

      sectionNl("override fun createDetachedEntity(parents: List<${WorkspaceEntity}>): $WorkspaceEntity") {
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
              line("this.${it.name} = parents.filterIsInstance<${parentType.type.javaType}>().singleOrNull()")
            } else {
              line("parents.filterIsInstance<${parentType.javaType}>().singleOrNull()?.let { this.${it.name} = it }")
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

      cacheCollector(this@lines)
    }
  }
}

fun List<ObjProperty<*, *>>.noRefs(): List<ObjProperty<*, *>> = this.filterNot { it.valueType.isRefType() }
fun List<ObjProperty<*, *>>.noEntitySource() = this.filter { it.name != "entitySource" }
fun List<ObjProperty<*, *>>.noSymbolicId() = this.filter { it.name != "symbolicId" }
fun List<ObjProperty<*, *>>.noOptional() = this.filter { it.valueType !is ValueType.Optional<*> }
fun List<ObjProperty<*, *>>.noDefaultValue() = this.filter { it.valueKind == ObjProperty.ValueKind.Plain }

private fun ObjClass<*>.cacheCollector(linesBuilder: LinesBuilder) {
  val clazzes = HashSet<String>()
  val accessors = HashSet<String>()
  val objects = HashSet<String>()
  val res = allFields.map {
    it.valueType.getClasses(it.name, clazzes, accessors, objects)
  }.all{ it }
  linesBuilder.section("override fun collectClassUsagesData(collector: ${UsedClassesCollector})") {
    clazzes.forEach {
      line("collector.add(${it.toQualifiedName()}::class.java)")
    }
    objects.forEach {
      line("collector.addObject(${it.toQualifiedName()}::class.java)")
    }
    accessors.forEach {
      line(it)
    }
    line("collector.sameForAllEntities = $res")
  }
}

private fun ValueType<*>.getClasses(fieldName: String, clazzes: HashSet<String>, accessors: HashSet<String>, objects: HashSet<String>): Boolean {
  var res = true
  when (this) {
    is ValueType.List<*> -> {
      if (!this.isRefType()) {
        accessors.add("this.$fieldName?.let { collector.add(it::class.java) }")
        res = false
      }
      this.elementType.getClasses(fieldName, clazzes, accessors, objects)
      return res
    }
    is ValueType.Set<*> -> {
      if (!this.isRefType()) {
        accessors.add("this.$fieldName?.let { collector.add(it::class.java) }")
        res = false
      }
      this.elementType.getClasses(fieldName, clazzes, accessors, objects)
      return res
    }
    is ValueType.Blob -> {
      val className = this.javaClassName
      if (className !in setOf(VirtualFileUrl.decoded, EntitySource.decoded, SymbolicEntityId.decoded)) {
        accessors.add("this.$fieldName?.let { collector.addDataToInspect(it) }")
        return res
      }
      if (className == VirtualFileUrl.decoded) {
        accessors.add("this.$fieldName?.let { collector.add(it::class.java) }")
        return false
      }
      return true
    }
    is ValueType.Enum -> {
      val className = this.javaClassName
      clazzes.add(className)
      return res
    }
    is ValueType.DataClass -> {
      // Here we might filter SymbolicIds and get them from the index, but in this case we would need to inspect their fields on the fly
      // Here we have all the information about the symbolic ids and it's fields, so let's keep them here (at least for a while).
      clazzes.add(javaClassName)
      properties.forEach { property ->
        property.type.getClasses(fieldName, clazzes, accessors, objects)
      }
      return true
    }
    is ValueType.SealedClass -> {
      clazzes.add(javaClassName)
      this.subclasses.forEach { subclass ->
        subclass.getClasses(fieldName, clazzes, accessors, objects)
      }
      return true
    }
    is ValueType.Map<*, *> -> {
      if (!this.isRefType()) {
        accessors.add("this.$fieldName?.let { collector.add(it::class.java) }")
        res = false
      }
      this.keyType.getClasses(fieldName, clazzes, accessors, objects)
      this.valueType.getClasses(fieldName, clazzes, accessors, objects)
      return res
    }
    is ValueType.Optional -> {
      return this.type.getClasses(fieldName, clazzes, accessors, objects)
    }
    is ValueType.Structure -> {
      this.fields.forEach {
        it.getClasses(fieldName, clazzes, accessors, objects)
      }
      return true
    }
    is ValueType.Object<*> -> {
      objects.add(javaClassName)
      return true
    }
    else -> return true
  }
}
