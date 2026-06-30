// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.classes.noDefaultValue
import com.intellij.workspaceModel.codegen.impl.writer.classes.noEntitySource
import com.intellij.workspaceModel.codegen.impl.writer.classes.noOptional
import com.intellij.workspaceModel.codegen.impl.writer.classes.noRefs
import com.intellij.workspaceModel.codegen.impl.writer.classes.noSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.extensions.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allExtensions
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isStandardInterface
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.requiresCompatibility
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType
import com.intellij.workspaceModel.codegen.impl.writer.fields.wsCode

fun generateMutableCode(objClass: ObjClass<*>, reporter: ProblemReporter): String = lines {
  if (objClass.additionalAnnotations.isNotEmpty()) {
    list(objClass.additionalAnnotations)
  }
  line("@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
  val (typeParameter, typeDeclaration) = if (objClass.builderWithTypeParameter) "T" to "<T: ${objClass.javaFullName}>" else objClass.javaFullName to ""
  val superBuilders = objClass.superTypes.filterIsInstance<ObjClass<*>>().filter { !it.isStandardInterface }.joinToString {
    ", ${it.javaBuilderName}<$typeParameter>"
  }
  val header = "${generatedCodeVisibilityModifier}interface ${objClass.defaultJavaBuilderName}$typeDeclaration: ${WorkspaceEntity.Builder}<$typeParameter>$superBuilders"

  section(header) {
    for (field in objClass.allFields.noSymbolicId()) {
      checkProperty(field, reporter)
      if (reporter.hasErrors()) return@generateMutableCode ""
      line(getWsBuilderApi(field, objClass))
    }
  }
}

fun generateEntityTypeObject(objClass: ObjClass<*>): String = lines {
  val builderGeneric = if (objClass.openness.extendable) "<${objClass.javaFullName}>" else ""
  val mandatoryFields = objClass.allFields.mandatoryFields()
  section("internal object ${objClass.javaFullName}Type : ${EntityType}<${objClass.javaFullName}, ${objClass.defaultJavaBuilderName}$builderGeneric>()") {
    line("override val entityImplClass: Class<*> get() = ${objClass.javaImplName}::class.java")
    line("override val entityImplBuilderClass: Class<*> get() = ${objClass.javaImplBuilderName}::class.java")
    if (mandatoryFields.isNotEmpty()) {
      line("operator fun invoke(")
      mandatoryFields.forEach { field ->
        line("${field.name}: ${field.valueType.javaType},")
      }
      line("init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null,")
      section("): ${objClass.defaultJavaBuilderName}$builderGeneric") {
        line("val builder = builder()")
        list(mandatoryFields) {
          if (this.valueType is ValueType.Set<*> && !this.valueType.isRefType()) {
            "builder.$name = $name.${StorageCollection.toMutableWorkspaceSet}()"
          }
          else if (this.valueType is ValueType.List<*> && !this.valueType.isRefType()) {
            "builder.$name = $name.${StorageCollection.toMutableWorkspaceList}()"
          }
          else {
            "builder.$name = $name"
          }
        }
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
    else {
      section("${generatedCodeVisibilityModifier}operator fun invoke(init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null): ${objClass.defaultJavaBuilderName}$builderGeneric") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
    if (objClass.requiresCompatibility) {
      compatibilityInvoke(mandatoryFields, objClass.javaFullName, builderGeneric)
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

fun generateTopLevelCode(objClass: ObjClass<*>, reporter: ProblemReporter): String {
  val implClassImport = if (objClass.openness.instantiatable) "import ${objClass.module.name}.impl.${objClass.javaImplName}" else ""
  val header = "@file:JvmName(\"${objClass.name}Modifications\")\npackage ${objClass.module.name}\n$implClassImport"
  val mutableCode = generateMutableCode(objClass, reporter)
  val entityTypeObject = if (objClass.openness.instantiatable) "\n${generateEntityTypeObject(objClass)}" else ""
  var result = "$header\n$mutableCode$entityTypeObject"
  val extensions = generateExtensionCode(objClass)
  if (extensions != null) {
    result = "$result\n$extensions"
  }
  val constructor = generateConstructorCode(objClass)
  if (constructor != null) {
    result = "$result\n$constructor"
  }
  return result
}

fun generateConstructorCode(objClass: ObjClass<*>): String? {
  if (objClass.openness == ObjClass.Openness.abstract) return null
  val mandatoryFields = objClass.allFields.mandatoryFields()
  val builderGeneric = if (objClass.openness.extendable) "<${objClass.javaFullName}>" else ""

  return lines {
    if (objClass.additionalAnnotations.isNotEmpty()) {
      list(objClass.additionalAnnotations)
    }
    line("@${JvmOverloads::class.fqn}")
    line("@${JvmName::class.fqn}(\"create${objClass.name}\")")
    if (mandatoryFields.isNotEmpty()) {
      line("${generatedCodeVisibilityModifier}fun ${objClass.name}(")
      mandatoryFields.forEach { field ->
        line("${field.name}: ${field.valueType.javaType},")
      }
      line("init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null,")
      line("): ${objClass.defaultJavaBuilderName} = ${objClass.name}Type(${mandatoryFields.joinToString(", ") { it.name }}, init)")
    }
    else {
      line("${generatedCodeVisibilityModifier}fun ${objClass.name}(init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null): ${objClass.defaultJavaBuilderName} = ${objClass.name}Companion(init)")
    }
  }
}

fun generateExtensionCode(objClass: ObjClass<*>): String? {
  val fields = objClass.allExtensions
  if (objClass.openness.extendable && fields.isEmpty()) return null

  return lines {
    if (!objClass.openness.extendable) {
      if (objClass.additionalAnnotations.isNotEmpty()) {
        list(objClass.additionalAnnotations)
      }
      line("${generatedCodeVisibilityModifier}fun ${MutableEntityStorage}.modify${objClass.name}(")
      line("entity: ${objClass.name},")
      line("modification: ${objClass.defaultJavaBuilderName}.() -> Unit,")
      line("): ${objClass.name} = modifyEntity(${objClass.defaultJavaBuilderName}::class.java, entity, modification)")
      
      if (objClass.requiresCompatibility) {
        objClass.compatibilityModifyCode(this)
      }
    }
    fields.sortedWith(compareBy({ it.receiver.name }, { it.name })).forEach { line(it.wsCode) }
    if (objClass.requiresCompatibility) {
      fields.sortedWith(compareBy({ it.receiver.name }, { it.name })).forEach { it.compatibilityExtensionWsCode(this@lines) }
    }
  }
}

fun getWsBuilderApi(objProperty: ObjProperty<*, *>, objClass: ObjClass<*>): String {
  val override = if (objProperty.receiver != objClass) "override " else ""
  val returnType = when {
    objProperty.valueType is ValueType.Collection<*, *> && !objProperty.valueType.isRefType() -> objProperty.valueType.javaMutableType
    else -> objProperty.valueType.javaBuilderTypeWithGeneric
  }
  return "${generatedCodeVisibilityModifier}${override}var ${objProperty.javaName}: $returnType"
}
