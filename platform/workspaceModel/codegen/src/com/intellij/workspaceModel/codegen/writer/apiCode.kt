// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.SKIPPED_TYPES
import com.intellij.workspaceModel.codegen.classes.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.codegen.fields.javaType
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.codegen.fields.referencedField
import com.intellij.workspaceModel.codegen.fields.wsCode
import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.WsEntityInterface
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.deft.Field
import com.intellij.workspaceModel.codegen.fields.builderApi

fun DefType.generatedApiCode(indent: String = "    ", isEmptyGenBlock: Boolean): String = lines(indent) {
  if (isEmptyGenBlock) line("//region generated code") else result.append("//region generated code\n")
  line("//@formatter:off")

  line("@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})")
  val abstractSupertype = if (base?.abstract == true) base else null
  val header = when {
    abstract && abstractSupertype != null -> {
      "interface Builder<T: $javaFullName>: $javaFullName, ${abstractSupertype.name}.Builder<T>, ${
        ModifiableWorkspaceEntity::class.fqn
      }<T>, ObjBuilder<T>"
    }
    abstractSupertype != null -> {
      "interface Builder: $javaFullName, ${abstractSupertype.name}.Builder<$javaFullName>, ${
        ModifiableWorkspaceEntity::class.fqn
      }<$javaFullName>, ObjBuilder<$javaFullName>"
    }
    abstract -> "interface Builder<T: $javaFullName>: $javaFullName, ${ModifiableWorkspaceEntity::class.fqn}<T>, ObjBuilder<T>"
    else -> "interface Builder: $javaFullName, ${ModifiableWorkspaceEntity::class.fqn}<$javaFullName>, ObjBuilder<$javaFullName>"
  }

  section(header) {
    list(structure.allFields.noPersistentId()) {
      if (def.kind is WsEntityInterface) wsBuilderApi else builderApi
    }
  }

  line()
  val builderGeneric = if (abstract) "<$javaFullName>" else ""
  val companionObjectHeader = buildString {
    append("companion object: ${Type::class.fqn}<$javaFullName, Builder$builderGeneric>(")
    val base = base
    if (base != null && base.name !in SKIPPED_TYPES)
      append(base.javaFullName)
    append(")")
  }
  val mandatoryFields = structure.allFields.noRefs().noOptional().noPersistentId().noDefaultValue()
  if (!mandatoryFields.isEmpty()) {
    val fields = (mandatoryFields.noEntitySource() + mandatoryFields.first { it.name == "entitySource" }).joinToString { "${it.name}: ${it.type.javaType}" }
    section(companionObjectHeader) {
      section("operator fun invoke($fields, init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        list(mandatoryFields) {
          "builder.$name = $name"
        }
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
  } else {
    section(companionObjectHeader) {
      section("operator fun invoke(init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
  }
  line("//@formatter:on")
  lineNoNl("//endregion")
}

fun DefType.generatedExtensionCode(indent: String = "    "): String {
  val extFields = module.extFields.filter { it.owner is DefType &&
                                              (this === it.owner || (it.owner.def.formExternalModule &&  it.referencedField.owner === this)) }
  if (extFields.isEmpty() && abstract) return ""
  return lines(indent) {
    line("//region generated code")
    if (!abstract) {
      line("fun ${MutableEntityStorage::class.fqn}.modifyEntity(entity: $name, modification: $name.Builder.() -> Unit) = modifyEntity($name.Builder::class.java, entity, modification)")
    }
    if (extFields.isNotEmpty()) {
      extFields.sortedWith(compareBy({it.owner.name}, {it.name})).forEach { line(it.wsCode) }
    }
    lineNoNl("//endregion")
  }
}

val Field<*, *>.wsBuilderApi: String
  get() = "override var $javaName: ${type.javaType}"

