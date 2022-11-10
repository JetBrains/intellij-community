// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.codegen.classes.*
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.engine.impl.ProblemReporter
import com.intellij.workspaceModel.codegen.fields.javaMutableType
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.fields.wsCode
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.fqn7
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.writer.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

val SKIPPED_TYPES: Set<String> = setOfNotNull(WorkspaceEntity::class.simpleName,
                                              WorkspaceEntity.Builder::class.simpleName,
                                              WorkspaceEntityWithSymbolicId::class.simpleName)

fun ObjClass<*>.generateBuilderCode(reporter: ProblemReporter): String = lines {
  checkSuperTypes(this@generateBuilderCode, reporter)
  line("@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})")
  val (typeParameter, typeDeclaration) = 
    if (openness.extendable) "T" to "<T: $javaFullName>" else javaFullName to ""
  val superBuilders = superTypes.filterIsInstance<ObjClass<*>>().filter { !it.isStandardInterface }.joinToString { 
    ", ${it.name}.Builder<$typeParameter>"
  }
  val header = "interface Builder$typeDeclaration: $javaFullName$superBuilders, ${WorkspaceEntity.Builder::class.fqn}<$typeParameter>, ${ObjBuilder::class.fqn}<$typeParameter>"

  section(header) {
    list(allFields.noSymbolicId()) {
      checkProperty(this, reporter)
      wsBuilderApi
    }
  }
}

fun checkSuperTypes(objClass: ObjClass<*>, reporter: ProblemReporter) {
  objClass.superTypes.filterIsInstance<ObjClass<*>>().forEach {superClass -> 
    if (!superClass.openness.extendable) {
      reporter.reportProblem(GenerationProblem("Class '${superClass.name}' cannot be extended", GenerationProblem.Level.ERROR, 
                                               ProblemLocation.Class(objClass)))
    }
    else if (!superClass.openness.openHierarchy && superClass.module != objClass.module) {
      reporter.reportProblem(GenerationProblem("Class '${superClass.name}' cannot be extended from other modules", 
                                               GenerationProblem.Level.ERROR, ProblemLocation.Class(objClass)))
    }
  }
}

private fun checkProperty(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  checkInheritance(objProperty, reporter)
  checkPropertyType(objProperty, reporter)
}

fun checkInheritance(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  objProperty.receiver.allSuperClasses.mapNotNull { it.fieldsByName[objProperty.name] }.forEach { overriddenField -> 
    if (!overriddenField.open) {
      reporter.reportProblem(
        GenerationProblem("Property '${overriddenField.receiver.name}::${overriddenField.name}' cannot be overriden",
                          GenerationProblem.Level.ERROR, ProblemLocation.Property(objProperty)))
    }
  }
}

private fun checkPropertyType(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  val errorMessage = when (val type = objProperty.valueType) {
    is ValueType.ObjRef<*> -> {
      if (type.child) "Child references should always be nullable"
      else null
    }
    else -> checkType(type)
  }
  if (errorMessage != null) {
    reporter.reportProblem(GenerationProblem(errorMessage, GenerationProblem.Level.ERROR, ProblemLocation.Property(objProperty)))
  }
}

private fun checkType(type: ValueType<*>): String? = when (type) {
  is ValueType.Optional -> when (type.type) {
    is ValueType.List<*> -> "Optional lists aren't supported"
    is ValueType.Set<*> -> "Optional sets aren't supported"
    else -> checkType(type.type)
  }
  is ValueType.Set<*> -> {
    if (type.elementType.isRefType()) {
      "Set of references isn't supported"
    }
    else checkType(type.elementType)
  }
  is ValueType.Map<*, *> -> {
    checkType(type.keyType) ?: checkType(type.valueType)
  }
  is ValueType.Blob<*> -> {
    if (!keepUnknownFields && type.javaClassName !in knownInterfaces) {
      "Unsupported type '${type.javaClassName}'"
    }
    else null
  }
  else -> null
}

private val keepUnknownFields: Boolean
  get() = Registry.`is`("workspace.model.generator.keep.unknown.fields")

private val knownInterfaces = setOf(
  VirtualFileUrl::class.qualifiedName!!,
  EntitySource::class.qualifiedName!!,
  SymbolicEntityId::class.qualifiedName!!,
)

fun ObjClass<*>.generateCompanionObject(): String = lines {
  val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""
  val companionObjectHeader = buildString {
    append("companion object: ${Type::class.fqn}<$javaFullName, Builder$builderGeneric>(")
    val base = superTypes.filterIsInstance<ObjClass<*>>().firstOrNull()
    if (base != null && base.name !in SKIPPED_TYPES)
      append(base.javaFullName)
    append(")")
  }
  val mandatoryFields = allFields.mandatoryFields()
  if (mandatoryFields.isNotEmpty()) {
    val fields = mandatoryFields.joinToString { "${it.name}: ${it.valueType.javaType}" }
    section(companionObjectHeader) {
      line("@${JvmOverloads::class.fqn}")
      line("@${JvmStatic::class.fqn}")
      line("@${JvmName::class.fqn}(\"create\")")
      section("operator fun invoke($fields, init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        list(mandatoryFields) {
          if (this.valueType is ValueType.Set<*> && !this.valueType.isRefType()) {
            "builder.$name = $name.${fqn7(Collection<*>::toMutableWorkspaceSet)}()"
          } else if (this.valueType is ValueType.List<*> && !this.valueType.isRefType()) {
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
      line("@${JvmOverloads::class.fqn}")
      line("@${JvmStatic::class.fqn}")
      line("@${JvmName::class.fqn}(\"create\")")
      section("operator fun invoke(init: (Builder$builderGeneric.() -> Unit)? = null): $javaFullName") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
  }
}

fun List<OwnProperty<*, *>>.mandatoryFields(): List<ObjProperty<*, *>> {
  var fields = this.noRefs().noOptional().noSymbolicId().noDefaultValue()
  if (fields.isNotEmpty()) {
    fields = fields.noEntitySource() + fields.single { it.name == "entitySource" }
  }
  return fields
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
    val returnType = if (valueType is ValueType.Collection<*, *> && !valueType.isRefType()) valueType.javaMutableType else valueType.javaType
    return "override var $javaName: $returnType"
  }

