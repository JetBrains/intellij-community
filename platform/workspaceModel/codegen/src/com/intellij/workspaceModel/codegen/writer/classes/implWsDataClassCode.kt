// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.codegen.InterfaceTraverser
import com.intellij.workspaceModel.codegen.InterfaceVisitor
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.implFieldName
import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.javaImplBuilderName
import com.intellij.workspaceModel.codegen.javaImplName
import org.jetbrains.deft.Obj
import com.intellij.workspaceModel.codegen.fields.implWsDataFieldCode
import com.intellij.workspaceModel.codegen.fields.implWsDataFieldInitializedCode
import com.intellij.workspaceModel.codegen.isRefType
import com.intellij.workspaceModel.codegen.sups
import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.WsEntityWithPersistentId
import com.intellij.workspaceModel.codegen.utils.LinesBuilder
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.TOptional
import com.intellij.workspaceModel.codegen.deft.ValueType
import com.intellij.workspaceModel.codegen.deft.Field
import org.jetbrains.kotlin.utils.addToStdlib.popLast

/**
 * - Soft links
 * - with PersistentId
 */

val ObjType<*, *>.javaDataName
  get() = "${name.replace(".", "")}Data"

val ObjType<*, *>.isEntityWithPersistentId: Boolean
  get() = (this as? DefType)?.def?.kind is WsEntityWithPersistentId

fun DefType.implWsDataClassCode(simpleTypes: List<DefType>): String {
  val entityDataBaseClass = if (isEntityWithPersistentId) {
    "${WorkspaceEntityData::class.fqn}.WithCalculablePersistentId<$javaFullName>()"
  }
  else {
    "${WorkspaceEntityData::class.fqn}<$javaFullName>()"
  }
  val hasSoftLinks = hasSoftLinks(simpleTypes)
  val softLinkable = if (hasSoftLinks) SoftLinkable::class.fqn else null
  return lines {
    section("class $javaDataName : ${sups(entityDataBaseClass, softLinkable)}") label@{
      listNl(structure.allFields.noRefs().noEntitySource().noPersistentId()) { implWsDataFieldCode }

      listNl(structure.allFields.noRefs().noEntitySource().noPersistentId().noOptional().noDefaultValue()) { implWsDataFieldInitializedCode }

      softLinksCode(this@label, hasSoftLinks, simpleTypes)

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
        list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
          if (hasSetter) {
            "entity.$implFieldName = $name"
          } else {
            "entity.$name = $name"
          }
        }
        line("entity.entitySource = entitySource")
        line("entity.snapshot = snapshot")
        line("entity.id = createEntityId()")
        line("return entity")
      }

      if (isEntityWithPersistentId) {
        val persistentIdField = structure.declaredFields.first { it.name == "persistentId" }
        assert(persistentIdField.hasDefault == Field.Default.plain)
        val methodBody = persistentIdField.defaultValue!!
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

        list(structure.allFields.noRefs().noPersistentId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- equalsIgnoringEntitySource
      sectionNl("override fun equalsIgnoringEntitySource(other: Any?): Boolean") {
        line("if (other == null) return false")
        line("if (this::class != other::class) return false")

        lineWrapped("other as $javaDataName")

        list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
          "if (this.$name != other.$name) return false"
        }

        line("return true")
      }

      // --- hashCode
      section("override fun hashCode(): Int") {
        line("var result = entitySource.hashCode()")
        list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
          "result = 31 * result + $name.hashCode()"
        }
        line("return result")
      }
    }
  }
}

class DeserializationVisitor(linesBuilder: LinesBuilder) : InterfaceVisitor {
  private val builders: ArrayDeque<LinesBuilder> = ArrayDeque<LinesBuilder>().also { it.add(linesBuilder) }
  private val builder: LinesBuilder
    get() = builders.last()

  private var countersCounter = 0

  override fun visitBoolean(varName: String): Boolean {
    builder.line("$varName = de.readBoolean()")
    return true
  }

  override fun visitInt(varName: String): Boolean {
    builder.line("$varName = de.readInt()")
    return true
  }

  override fun visitString(varName: String): Boolean {
    builder.line("$varName = de.readString()")
    return true
  }

  override fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), "${builder.indent}    ")
    builders.add(sub)
    return true
  }

  override fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val myInListBuilder = builders.popLast()
    if (myInListBuilder.result.isNotBlank()) {
      val varCounter = "counter" + counter()
      val varCollector = "collector" + counter()
      builder.line("val $varCounter = de.readInt()")
      builder.line("val $varCollector = ArrayList<${listArgumentType.javaType}>()")
      builder.line("var $itemVarName: ${listArgumentType.javaType}")
      builder.line("repeat($varCounter) {")
      myInListBuilder.line("$varCollector.add($itemVarName)")
      builder.result.append(myInListBuilder.result)
      builder.line("}")
      builder.line("$varName = $varCollector")
    }
    return true
  }

  override fun visitMapStart(varName: String,
                             keyVarName: String,
                             valueVarName: String,
                             keyType: ValueType<*>,
                             valueType: ValueType<*>): Boolean {
    return false
  }

  override fun visitMapEnd(varName: String,
                           keyVarName: String,
                           valueVarName: String,
                           keyType: ValueType<*>,
                           valueType: ValueType<*>,
                           traverseResult: Boolean): Boolean {
    return false
  }

  override fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean {
    if (type.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), "${builder.indent}    ")
    builders.add(sub)
    return true
  }

  override fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean {
    if (type.isRefType()) return true

    val popLast = builders.popLast()
    builder.section("if (de.acceptNull())") {
      line("$varName = null")
    }
    builder.section("else") {
      if (popLast.result.isNotBlank()) {
        line("val $notNullVarName: ${type.javaType}")
        result.append(popLast.result)
        line("$varName = $notNullVarName")
      }
    }
    return true
  }

  override fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean {
    return false
  }

  override fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean {
    return false
  }

  override fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean {
    return false
  }

  override fun visitDataClassStart(varName: String, javaSimpleName: String, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, javaSimpleName: String, traverseResult: Boolean, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }

  private fun counter(): Int {
    return countersCounter++
  }
}

class SerializatorVisitor private constructor(private val linesBuilder: ArrayDeque<LinesBuilder>) : InterfaceVisitor {

  constructor(linesBuilder: LinesBuilder) : this(ArrayDeque<LinesBuilder>().also { it.add(linesBuilder) })

  val builder: LinesBuilder
    get() = linesBuilder.last()

  override fun visitBoolean(varName: String): Boolean {
    builder.line("ser.saveBoolean($varName)")
    return true
  }

  override fun visitInt(varName: String): Boolean {
    builder.line("ser.saveInt($varName)")
    return true
  }

  override fun visitString(varName: String): Boolean {
    builder.line("ser.saveString($varName)")
    return true
  }

  override fun visitListStart(varName: String, itemVarName: String, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), "${builder.indent}    ")
    linesBuilder.add(sub)
    return true
  }

  override fun visitListEnd(varName: String, itemVarName: String, traverseResult: Boolean, listArgumentType: ValueType<*>): Boolean {
    if (listArgumentType.isRefType()) return true

    val myInListBuilder = linesBuilder.popLast()
    if (myInListBuilder.result.isNotBlank()) {
      builder.line("ser.saveInt($varName.size)")
      builder.line("for ($itemVarName in $varName) {")
      builder.result.append(myInListBuilder.result)
      builder.line("}")
    }
    return true
  }

  override fun visitMapStart(varName: String,
                             keyVarName: String,
                             valueVarName: String,
                             keyType: ValueType<*>,
                             valueType: ValueType<*>): Boolean {
    if (keyType.isRefType() || valueType.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), "${builder.indent}    ")
    linesBuilder.add(sub)
    return true
  }

  override fun visitMapEnd(varName: String,
                           keyVarName: String,
                           valueVarName: String,
                           keyType: ValueType<*>,
                           valueType: ValueType<*>,
                           traverseResult: Boolean): Boolean {
    if (keyType.isRefType() || valueType.isRefType()) return true

    val inMapBuilder = linesBuilder.popLast()
    if (inMapBuilder.result.isNotBlank()) {
      builder.line("ser.saveInt($varName.size)")
      builder.line("for (($keyVarName, $valueVarName) in $varName) {")
      builder.result.append(inMapBuilder.result)
      builder.line("}")
    }
    return true
  }

  override fun visitOptionalStart(varName: String, notNullVarName: String, type: ValueType<*>): Boolean {
    if (type.isRefType()) return true

    val sub = LinesBuilder(StringBuilder(), "${builder.indent}    ")
    linesBuilder.add(sub)
    return true
  }

  override fun visitOptionalEnd(varName: String, notNullVarName: String, type: ValueType<*>, traverseResult: Boolean): Boolean {
    if (type.isRefType()) return true

    val inMapBuilder = linesBuilder.popLast()
    if (inMapBuilder.result.isNotBlank()) {
      builder.line("val $notNullVarName = $varName")
      builder.line("if ($notNullVarName != null) {")
      builder.result.append(inMapBuilder.result)
      builder.line("} else {")
      builder.line("    ser.saveNull()")
      builder.line("}")
    }
    return true
  }

  override fun visitUnknownBlob(varName: String, javaSimpleName: String): Boolean {
    if (javaSimpleName == "EntitySource") return true
    builder.line("ser.saveBlob($varName, \"$javaSimpleName\")")
    return true
  }

  override fun visitKnownBlobStart(varName: String, javaSimpleName: String): Boolean {
    return true
  }

  override fun visitKnownBlobFinish(varName: String, javaSimpleName: String, traverseResult: Boolean): Boolean {
    return true
  }

  override fun visitDataClassStart(varName: String, javaSimpleName: String, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }

  override fun visitDataClassEnd(varName: String, javaSimpleName: String, traverseResult: Boolean, foundType: DefType): Boolean {
    TODO("Not yet implemented")
  }
}

fun List<Field<out Obj, Any?>>.noRefs(): List<Field<out Obj, Any?>> = this.filterNot { it.type.isRefType() }
fun List<Field<out Obj, Any?>>.noEntitySource() = this.filter { it.name != "entitySource" }
fun List<Field<out Obj, Any?>>.noPersistentId() = this.filter { it.name != "persistentId" }
fun List<Field<out Obj, Any?>>.noOptional() = this.filter { it.type !is TOptional<*> }
fun List<Field<out Obj, Any?>>.noDefaultValue() = this.filter { it.defaultValue == null }
fun List<Field<out Obj, Any?>>.noId() = this.filter { it.name != "id" }
fun List<Field<out Obj, Any?>>.noSnapshot() = this.filter { it.name != "snapshot" }
