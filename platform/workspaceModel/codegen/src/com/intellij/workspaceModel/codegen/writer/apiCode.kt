// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.classes.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.fields.javaMutableType
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.fields.wsCode
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.fqn7
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.writer.allFields
import com.intellij.workspaceModel.codegen.writer.isStandardInterface
import com.intellij.workspaceModel.codegen.writer.javaName
import com.intellij.workspaceModel.codegen.writer.type
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

fun ObjClass<*>.generateBuilderCode(): String = lines {
  line("@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})")
  val (typeParameter, typeDeclaration) = 
    if (openness.extendable) "T" to "<T: $javaFullName>" else javaFullName to ""
  val superBuilders = superTypes.filterIsInstance<ObjClass<*>>().filter { !it.isStandardInterface }.joinToString { 
    ", ${it.name}.Builder<$typeParameter>"
  }
  val header = "interface Builder$typeDeclaration: $javaFullName$superBuilders, ${ModifiableWorkspaceEntity::class.fqn}<$typeParameter>, ${ObjBuilder::class.fqn}<$typeParameter>"

  section(header) {
    list(allFields.noPersistentId()) {
      wsBuilderApi
    }
  }
}

fun ObjClass<*>.generateCompanionObject(): String = lines {
  val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""
  val companionObjectHeader = buildString {
    append("companion object: ${Type::class.fqn}<$javaFullName, Builder$builderGeneric>(")
    val base = superTypes.filterIsInstance<ObjClass<*>>().firstOrNull()
    if (base != null && base.name !in SKIPPED_TYPES)
      append(base.javaFullName)
    append(")")
  }
  val mandatoryFields = allFields.noRefs().noOptional().noPersistentId().noDefaultValue()
  if (!mandatoryFields.isEmpty()) {
    val fields = (mandatoryFields.noEntitySource() + mandatoryFields.first { it.name == "entitySource" }).joinToString { "${it.name}: ${it.type.javaType}" }
    section(companionObjectHeader) {
      section("operator fun invoke($fields, init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        list(mandatoryFields) {
          if (this.type is ValueType.Set<*> && !this.type.isRefType()) {
            "builder.$name = $name.${fqn7(Collection<*>::toMutableWorkspaceSet)}()"
          } else if (this.type is ValueType.List<*> && !this.type.isRefType()) {
            "builder.$name = $name.${fqn7(Collection<*>::toMutableWorkspaceList)}()"
          } else {
            "builder.$name = $name"
          }
        }
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
  }
  else {
    section(companionObjectHeader) {
      section("operator fun invoke(init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
  }
}


fun ObjClass<*>.generateExtensionCode(): String? {
  val fields = module.extensions.filter { it.receiver == this || it.receiver.module != module && it.valueType.isRefType() && it.valueType.getRefType().target == this }
  if (openness.extendable && fields.isEmpty()) return null

  return lines {
    if (!openness.extendable) {
      line("fun ${MutableEntityStorage::class.fqn}.modifyEntity(entity: $name, modification: $name.Builder.() -> Unit) = modifyEntity($name.Builder::class.java, entity, modification)")
    }
    fields.sortedWith(compareBy({ it.receiver.name }, { it.name })).forEach { line(it.wsCode) }
  }
}

val ObjProperty<*, *>.wsBuilderApi: String
  get() {
    val returnType = if (type is ValueType.Collection<*, *> && !type.isRefType()) type.javaMutableType else type.javaType
    return "override var $javaName: $returnType"
  }

