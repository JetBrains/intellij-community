// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.engine.*
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.classes.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.*

fun ObjClass<*>.generateMutableCode(reporter: ProblemReporter): String = lines {
  if (additionalAnnotations.isNotEmpty()) {
    line(additionalAnnotations)
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

fun checkSuperTypes(objClass: ObjClass<*>, reporter: ProblemReporter) {
  objClass.superTypes.filterIsInstance<ObjClass<*>>().forEach { superClass ->
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

fun checkSymbolicId(objClass: ObjClass<*>, reporter: ProblemReporter) {
  if (!objClass.isEntityWithSymbolicId) return
  if (objClass.openness == ObjClass.Openness.abstract) return
  if (objClass.fields.none { it.name == "symbolicId" }) {
    reporter.reportProblem(GenerationProblem("Class extends '${WorkspaceEntityWithSymbolicId.simpleName}' but " +
      "doesn't override 'WorkspaceEntityWithSymbolicId.getSymbolicId' property",
      GenerationProblem.Level.ERROR, ProblemLocation.Class(objClass)))
  }
}

private fun checkProperty(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  checkInheritance(objProperty, reporter)
  checkAllImmutable(objProperty, reporter)
  checkPropertyType(objProperty, reporter)
}

fun checkInheritance(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  objProperty.receiver.allSuperClasses.mapNotNull { it.fieldsByName[objProperty.name] }.forEach { overriddenField ->
    if (!overriddenField.open) {
      reporter.reportProblem(
        GenerationProblem("Property '${overriddenField.receiver.name}::${overriddenField.name}' cannot be overridden",
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

fun checkAllImmutable(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  if (objProperty.mutable) {
    reporter.reportProblem(GenerationProblem("An immutable interface can't contain mutable properties", GenerationProblem.Level.ERROR, ProblemLocation.Property(objProperty)))
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

  else -> null
}

private val knownInterfaces = setOf(VirtualFileUrl.decoded, EntitySource.decoded, SymbolicEntityId.decoded)

fun ObjClass<*>.generateEntityTypeObject(): String = lines {
  val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""
  val mandatoryFields = allFields.mandatoryFields()
  section("internal object ${javaFullName}Type : ${EntityType}<$javaFullName, $defaultJavaBuilderName$builderGeneric>()") {
    line("override val entityClass: Class<$javaFullName> get() = $javaFullName::class.java")
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
  val mutableCode = generateMutableCode(reporter)
  val entityTypeObject = generateEntityTypeObject()
  val header = """
    @file:JvmName("${name}Modifications")
    
    package ${module.name}
  """.trimIndent()
  var result = "$header\n$mutableCode\n$entityTypeObject"
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
      line(additionalAnnotations)
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
        line(additionalAnnotations)
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
  return "${override}var $javaName: $returnType"
}
