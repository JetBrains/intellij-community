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

fun ObjClass<*>.generateMutableCode(reporter: ProblemReporter): String = lines {
  if (additionalAnnotations.isNotEmpty()) {
    list(additionalAnnotations)
  }
  line("@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
  val (typeParameter, typeDeclaration) = if (builderWithTypeParameter) "T" to "<T: $javaFullName>" else javaFullName to ""
  val superBuilders = superTypes.filterIsInstance<ObjClass<*>>().filter { !it.isStandardInterface }.joinToString {
    ", ${it.javaBuilderName}<$typeParameter>"
  }
  val header = "${generatedCodeVisibilityModifier}interface $defaultJavaBuilderName$typeDeclaration: ${WorkspaceEntity.Builder}<$typeParameter>$superBuilders"

  section(header) {
    for (field in allFields.noSymbolicId()) {
      checkProperty(field, reporter)
      if (reporter.hasErrors()) return@generateMutableCode ""
      line(field.getWsBuilderApi(this@generateMutableCode))
    }
  }
}

fun ObjClass<*>.generateEntityTypeObject(): String = lines {
  val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""
  val mandatoryFields = allFields.mandatoryFields()
  section("internal object ${javaFullName}Type : ${EntityType}<$javaFullName, $defaultJavaBuilderName$builderGeneric>()") {
    line("override val entityImplClass: Class<*> get() = $javaImplName::class.java")
    line("override val entityImplBuilderClass: Class<*> get() = $javaImplBuilderName::class.java")
    if (mandatoryFields.isNotEmpty()) {
      line("operator fun invoke(")
      mandatoryFields.forEach { field ->
        line("${field.name}: ${field.valueType.javaType},")
      }
      line("init: ($defaultJavaBuilderName$builderGeneric.() -> Unit)? = null,")
      section("): $defaultJavaBuilderName$builderGeneric") {
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
      section("${generatedCodeVisibilityModifier}operator fun invoke(init: ($defaultJavaBuilderName$builderGeneric.() -> Unit)? = null): $defaultJavaBuilderName$builderGeneric") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
    if (requiresCompatibility) {
      compatibilityInvoke(mandatoryFields, javaFullName, builderGeneric)
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

fun ObjClass<*>.generateTopLevelCode(reporter: ProblemReporter): String {
  val implClassImport = if (openness.instantiatable) "import ${module.name}.impl.$javaImplName" else ""
  val header = "@file:JvmName(\"${name}Modifications\")\npackage ${module.name}\n$implClassImport"
  val mutableCode = generateMutableCode(reporter)
  val entityTypeObject = if (openness.instantiatable) "\n${generateEntityTypeObject()}" else ""
  var result = "$header\n$mutableCode$entityTypeObject"
  val extensions = generateExtensionCode()
  if (extensions != null) {
    result = "$result\n$extensions"
  }
  val constructor = generateConstructorCode()
  if (constructor != null) {
    result = "$result\n$constructor"
  }
  return result
}

fun ObjClass<*>.generateConstructorCode(): String? {
  if (openness == ObjClass.Openness.abstract) return null
  val mandatoryFields = allFields.mandatoryFields()
  val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""

  return lines {
    if (additionalAnnotations.isNotEmpty()) {
      list(additionalAnnotations)
    }
    line("@${JvmOverloads::class.fqn}")
    line("@${JvmName::class.fqn}(\"create$name\")")
    if (mandatoryFields.isNotEmpty()) {
      line("${generatedCodeVisibilityModifier}fun $name(")
      mandatoryFields.forEach { field ->
        line("${field.name}: ${field.valueType.javaType},")
      }
      line("init: ($defaultJavaBuilderName$builderGeneric.() -> Unit)? = null,")
      line("): $defaultJavaBuilderName = ${name}Type(${mandatoryFields.joinToString(", ") { it.name }}, init)")
    }
    else {
      line("${generatedCodeVisibilityModifier}fun $name(init: ($defaultJavaBuilderName$builderGeneric.() -> Unit)? = null): $defaultJavaBuilderName = ${name}Companion(init)")
    }
  }
}

fun ObjClass<*>.generateExtensionCode(): String? {
  val fields = allExtensions
  if (openness.extendable && fields.isEmpty()) return null

  return lines {
    if (!openness.extendable) {
      if (additionalAnnotations.isNotEmpty()) {
        list(additionalAnnotations)
      }
      line("${generatedCodeVisibilityModifier}fun ${MutableEntityStorage}.modify$name(")
      line("entity: $name,")
      line("modification: $defaultJavaBuilderName.() -> Unit,")
      line("): $name = modifyEntity($defaultJavaBuilderName::class.java, entity, modification)")
      
      if (requiresCompatibility) {
        compatibilityModifyCode(this@lines)
      }
    }
    fields.sortedWith(compareBy({ it.receiver.name }, { it.name })).forEach { line(it.wsCode) }
    if (requiresCompatibility) {
      fields.sortedWith(compareBy({ it.receiver.name }, { it.name })).forEach { it.compatibilityExtensionWsCode(this@lines) }
    }
  }
}

fun ObjProperty<*, *>.getWsBuilderApi(objClass: ObjClass<*>): String {
  val override = if (this.receiver != objClass) "override " else ""
  val returnType = when {
    valueType is ValueType.Collection<*, *> && !valueType.isRefType() -> valueType.javaMutableType
    else -> valueType.javaBuilderTypeWithGeneric
  }
  return "${generatedCodeVisibilityModifier}${override}var $javaName: $returnType"
}
