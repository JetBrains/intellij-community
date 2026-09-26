package com.intellij.workspaceModel.codegen.impl.writer.entityApi

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.dsl.apiVersionAnnotation
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.checkProperty
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isStandardInterface
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType

fun CodeContext.generateBuilderCode(objClass: ObjClass<*>) {
  additionalAnnotations(objClass)
  apiVersionAnnotation()
  val (typeParameter, typeDeclaration) = if (objClass.builderWithTypeParameter) "T" to "<T: ${objClass.javaFullName}>" else objClass.javaFullName to ""
  val superBuilders = objClass.superTypes.filterIsInstance<ObjClass<*>>().filter { !it.isStandardInterface }.joinToString {
    ", ${it.javaBuilderName}<$typeParameter>"
  }
  val header =
    "${explicitApiModifier}interface ${objClass.defaultJavaBuilderName}$typeDeclaration: ${WorkspaceEntity.Builder}<$typeParameter>$superBuilders"

  section(header) {
    for (property in getAllProperties(objClass, withSymbolicId = false)) {
      checkProperty(property)
      if (hasErrors()) continue
      val override = if (property.receiver != objClass) "override " else ""
      val returnType = when {
        property.valueType is ValueType.Collection<*, *> && !property.valueType.isReferenceType() -> getJavaMutableType(property)
        else -> getJavaBuilderTypeWithGeneric(property)
      }
      +"${explicitApiModifier}${override}var ${property.javaName}: $returnType"
    }
  }
}
