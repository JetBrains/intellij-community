package com.intellij.workspaceModel.codegen.impl.writer.entityApi

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.fqn
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType
import com.intellij.workspaceModel.codegen.impl.writer.mandatoryProperties

fun CodeContext.generateConstructorCode(objClass: ObjClass<*>) {
  if (objClass.openness == ObjClass.Openness.abstract) return
  val mandatoryFields = mandatoryProperties(objClass)
  val builderGeneric = if (objClass.openness.extendable) "<${objClass.javaFullName}>" else ""
  val javaBuilderName = objClass.defaultJavaBuilderName


  additionalAnnotations(objClass)
  annotation(JvmOverloads::class.fqn.toString())
  annotation("${JvmName::class.fqn}(\"create${objClass.name}\")")
  if (mandatoryFields.isNotEmpty()) {
    line("${this@generateConstructorCode.explicitApiModifier}fun ${objClass.name}(")
    mandatoryFields.forEach { field ->
      line("${field.name}: ${getJavaType(field)},")
    }
    line("init: ($javaBuilderName$builderGeneric.() -> Unit)? = null,")
    line("): $javaBuilderName = ${objClass.name}Type(${mandatoryFields.joinToString(", ") { it.name }}, init)")
  }
  else {
    line("${this@generateConstructorCode.explicitApiModifier}fun ${objClass.name}(init: ($javaBuilderName$builderGeneric.() -> Unit)? = null): $javaBuilderName = ${objClass.name}Type(init)")
  }

}