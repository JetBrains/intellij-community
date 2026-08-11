package com.intellij.workspaceModel.codegen.impl.writer.entityApi

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.writer.EntityType
import com.intellij.workspaceModel.codegen.impl.writer.StorageCollection
import com.intellij.workspaceModel.codegen.impl.writer.compatibilityInvoke
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.requiresCompatibility
import com.intellij.workspaceModel.codegen.impl.writer.getJavaType
import com.intellij.workspaceModel.codegen.impl.writer.mandatoryProperties

fun CodeContext.generateEntityTypeObject(objClass: ObjClass<*>) {
  val builderGeneric = if (objClass.openness.extendable) "<${objClass.javaFullName}>" else ""
  val mandatoryProperties = mandatoryProperties(objClass)
  section("internal object ${objClass.javaFullName}Type : ${EntityType}<${objClass.javaFullName}, ${objClass.defaultJavaBuilderName}$builderGeneric>()") {
    line("override val entityImplClass: Class<*> get() = ${objClass.javaImplName}::class.java")
    line("override val entityImplBuilderClass: Class<*> get() = ${objClass.javaImplBuilderName}::class.java")
    if (mandatoryProperties.isNotEmpty()) {
      line("operator fun invoke(")
      for (property in mandatoryProperties) {
        line("${property.name}: ${getJavaType(property)},")
      }
      line("init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null,")
      section("): ${objClass.defaultJavaBuilderName}$builderGeneric") {
        line("val builder = builder()")
        for (property in mandatoryProperties) {
          val name =property.name
          if (property.valueType is ValueType.Set<*> && !property.valueType.isReferenceType()) {
            +"builder.$name = $name.${StorageCollection.toMutableWorkspaceSet}()"
          }
          else if (property.valueType is ValueType.List<*> && !property.valueType.isReferenceType()) {
            +"builder.$name = $name.${StorageCollection.toMutableWorkspaceList}()"
          }
          else {
            +"builder.$name = $name"
          }
        }
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
    else {
      section("${this@generateEntityTypeObject.explicitApiModifier}operator fun invoke(init: (${objClass.defaultJavaBuilderName}$builderGeneric.() -> Unit)? = null): ${objClass.defaultJavaBuilderName}$builderGeneric") {
        line("val builder = builder()")
        line("init?.invoke(builder)")
        line("return builder")
      }
    }
    if (objClass.requiresCompatibility) {
      compatibilityInvoke(mandatoryProperties, objClass.javaFullName, builderGeneric)
    }
  }
}