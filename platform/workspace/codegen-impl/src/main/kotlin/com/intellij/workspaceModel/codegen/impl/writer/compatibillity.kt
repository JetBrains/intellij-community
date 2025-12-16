// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType
import java.util.Locale.getDefault

private val DEPRECATION = "@${Deprecated::class.fqn}(message = \"Use new API instead\")"

private fun String.capitalizeFirstChar(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

fun ObjClass<*>.generateCompatabilityBuilder(): String =
  lines {
    if (!requiresCompatibility) {
      return@lines
    }
    val (typeParameter, typeDeclaration) = if (builderWithTypeParameter) "<T>" to "<T: $javaFullName>" else "" to ""
    val message = "Use $defaultJavaBuilderName instead"
    line("@${Deprecated::class.fqn}(message = \"$message\")")
    val superFields = allSuperClasses.flatMap { it.allFields.map { it.name } }.toSet()
    val refsFields = allRefsFields.filter { it.name !in superFields  && it.valueType !is ValueType.Collection<*, *>}
    if (refsFields.isNotEmpty()) {
      section("interface Builder$typeDeclaration : $defaultJavaBuilderName$typeParameter") {
        refsFields
          .forEach { field ->
            line(DEPRECATION)
            line("fun get${field.name.capitalizeFirstChar()}():${field.valueType.compatibilityJavaBuilderTypeWithGeneric} = ${field.name} as ${field.valueType.compatibilityJavaBuilderTypeWithGeneric}")
            line(DEPRECATION)
            line("fun set${field.name.capitalizeFirstChar()}(value: ${field.valueType.compatibilityJavaBuilderTypeWithGeneric}) { ${field.name} = value }")
          }
      }
    }
    else {
      line("interface Builder$typeDeclaration : $defaultJavaBuilderName$typeParameter")
    }
  }

fun ObjClass<*>.generateCompatibilityCompanion(): String =
  lines {
    if (!requiresCompatibility) {
      return@lines
    }
    val builderGeneric = if (openness.extendable) "<$javaFullName>" else ""
    section("companion object : ${EntityType}<$javaFullName, Builder$builderGeneric>()") {
      val mandatoryFields = allFields.mandatoryFields()
      line(DEPRECATION)
      line("@${JvmOverloads::class.fqn}")
      line("@${JvmStatic::class.fqn}")
      line("@${JvmName::class.fqn}(\"create\")")
      if (mandatoryFields.isNotEmpty()) {
        line("operator fun invoke(")
        mandatoryFields.forEach { field ->
          line("${field.name}: ${field.valueType.javaType},")
        }
        line("init: (Builder$builderGeneric.() -> Unit)? = null,")
        line("): Builder$builderGeneric = ${javaFullName}Type.compatibilityInvoke(${mandatoryFields.joinToString(", ") { it.name }}, init)")
      }
      else {
        line("${generatedCodeVisibilityModifier}operator fun invoke(init: (Builder$builderGeneric.() -> Unit)? = null): Builder$builderGeneric = = ${javaFullName}Type.compatibilityInvoke(init)")
      }
    }
  }

fun LinesBuilder.compatibilityInvoke(
  mandatoryFields: List<ObjProperty<*, *>>,
  javaFullName: QualifiedName,
  builderGeneric: String,
) {
  val builderSymbol = "$javaFullName.Builder$builderGeneric"
  line(DEPRECATION)
  if (mandatoryFields.isNotEmpty()) {
    line("fun compatibilityInvoke(")
    mandatoryFields.forEach { field ->
      line("${field.name}: ${field.valueType.javaType},")
    }
    line("init: ($builderSymbol.() -> Unit)? = null,")
    section("): $builderSymbol") {
      line("val builder = builder() as $builderSymbol")
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
    section("${generatedCodeVisibilityModifier}fun compatibilityInvoke(init: ($builderSymbol.() -> Unit)? = null): $builderSymbol") {
      line("val builder = builder() as $builderSymbol")
      line("init?.invoke(builder)")
      line("return builder")
    }
  }
}

fun ObjClass<*>.compatibilityModifyCode(linesBuilder: LinesBuilder) {
  with(linesBuilder) {
    line(DEPRECATION)
    if (additionalAnnotations.isNotEmpty()) {
      line(additionalAnnotations)
    }
    line("${generatedCodeVisibilityModifier}fun ${MutableEntityStorage}.modify$name(")
    line("  entity: $name,")
    line("  modification: $compatibleJavaBuilderName.() -> Unit,")
    line("): $name {")
    line("  return modifyEntity($compatibleJavaBuilderName::class.java, entity, modification)")
    line("}")
  }
}

fun ExtProperty<*, *>.compatibilityExtensionWsCode(linesBuilder: LinesBuilder) {
  val isChild = valueType.getRefType().child
  val parentAnnotation = if (!isChild) "@${Parent}\n" else ""
  val generic = if (receiver.builderWithTypeParameter) "<out ${receiver.javaFullName}>" else ""
  if (additionalAnnotations.isNotEmpty()) {
    linesBuilder.line(additionalAnnotations)
  }
  linesBuilder.line(DEPRECATION)
  val propertyType = valueType.compatibilityJavaBuilderTypeWithGeneric
  linesBuilder.sectionNoBrackets("$parentAnnotation${generatedCodeVisibilityModifier}var ${receiver.compatibleJavaBuilderName}$generic.$name: $propertyType") {
    line("get() = (this as ${receiver.defaultJavaBuilderName}$generic).$name as $propertyType")
    section("set(value)") {
      line("(this as ${receiver.defaultJavaBuilderName}$generic).$name = value")
    }
  }
}

private val ObjClass<*>.compatibilityJavaBuilderFqnName: QualifiedName
  get() = fqn(module.name, "$name.Builder")

private val ValueType<*>.compatibilityJavaBuilderTypeWithGeneric: QualifiedName
  get() = when (this) {
    ValueType.Boolean -> "Boolean".toQualifiedName()
    ValueType.Int -> "Int".toQualifiedName()
    ValueType.String -> "String".toQualifiedName()
    is ValueType.List<*> -> "List".toQualifiedName().appendSuffix("<${elementType.compatibilityJavaBuilderTypeWithGeneric}>")
    is ValueType.Set<*> -> "Set".toQualifiedName().appendSuffix("<${elementType.compatibilityJavaBuilderTypeWithGeneric}>")
    is ValueType.Map<*, *> -> "Map".toQualifiedName().appendSuffix("<${keyType.compatibilityJavaBuilderTypeWithGeneric}, ${valueType.compatibilityJavaBuilderTypeWithGeneric}>")
    is ValueType.ObjRef -> {
      val out = if (target.openness == ObjClass.Openness.abstract) "out " else ""
      target.compatibilityJavaBuilderFqnName.appendSuffix(if (target.builderWithTypeParameter) "<$out${this.javaType}>" else "")
    }

    is ValueType.Optional<*> -> type.compatibilityJavaBuilderTypeWithGeneric.appendSuffix("?")
    is ValueType.JvmClass -> kotlinClassName.toQualifiedName()
    else -> throw UnsupportedOperationException("$this type isn't supported")
  }