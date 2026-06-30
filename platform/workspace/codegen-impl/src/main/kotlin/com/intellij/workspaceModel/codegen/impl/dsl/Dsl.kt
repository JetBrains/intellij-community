package com.intellij.workspaceModel.codegen.impl.dsl

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.writer.GeneratedCodeApiVersion
import com.intellij.workspaceModel.codegen.impl.writer.GeneratedCodeImplVersion
import com.intellij.workspaceModel.codegen.impl.writer.Internal
import com.intellij.workspaceModel.codegen.impl.writer.K1Deprecation
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityInternalApi

fun GeneratorContext.unsupportedTypeError(valueType: ValueType<*>, objProperty: ObjProperty<*, *>) {
  reportPropertyError("$valueType type isn't supported", objProperty)
}

fun GeneratorContext.notReferenceError(description: String, objProperty: ObjProperty<*, *>) {
  reportPropertyError("${objProperty.name} is not an entity reference, but is expected to be: $description", objProperty)
}

fun CodeContext.annotation(name: String) {
  +"@$name"
}

fun CodeContext.apiVersionAnnotation() {
  annotation("${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
}

fun CodeContext.implVersionAnnotation() {
  annotation("${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})")
}

fun CodeContext.optInWorkspaceEntityInternalApi() {
  annotation("OptIn($WorkspaceEntityInternalApi::class)")
}

fun CodeContext.additionalAnnotations(objClass: ObjClass<*>) {
  for (annotation in objClass.annotations) {
    when (annotation.fqName) {
      Internal.decoded -> annotation("$Internal")
      K1Deprecation.decoded -> annotation("$K1Deprecation")
      else -> { /* do nothing */ }
    }
  }
}

fun CodeContext.packageDirective(packageName: String) {
  +"package $packageName"
}
