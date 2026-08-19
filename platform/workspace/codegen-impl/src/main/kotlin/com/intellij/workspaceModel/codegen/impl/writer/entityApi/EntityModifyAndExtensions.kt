package com.intellij.workspaceModel.codegen.impl.writer.entityApi

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.notReferenceError
import com.intellij.workspaceModel.codegen.impl.writer.Internal
import com.intellij.workspaceModel.codegen.impl.writer.MutableEntityStorage
import com.intellij.workspaceModel.codegen.impl.writer.Parent
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntity
import com.intellij.workspaceModel.codegen.impl.writer.compatibilityExtensionWsCode
import com.intellij.workspaceModel.codegen.impl.writer.compatibilityModifyCode
import com.intellij.workspaceModel.codegen.impl.writer.extensions.builderWithTypeParameter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getAllExtensions
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.requiresCompatibility
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getEntityType
import com.intellij.workspaceModel.codegen.impl.writer.getJavaBuilderTypeWithGeneric

fun CodeContext.generateModifyAndExtensionCode(objClass: ObjClass<*>) {
  val allExtensions = getAllExtensions(objClass).sortedWith(compareBy({ it.receiver.name }, { it.name }))
  if (objClass.openness.extendable && allExtensions.isEmpty()) return

  if (!objClass.openness.extendable) {
    additionalAnnotations(objClass)
    line("${explicitApiModifier}fun ${MutableEntityStorage}.modify${objClass.name}(")
    line("entity: ${objClass.name},")
    line("modification: ${objClass.defaultJavaBuilderName}.() -> Unit,")
    line("): ${objClass.name} = modifyEntity(${objClass.defaultJavaBuilderName}::class.java, entity, modification)")

    if (objClass.requiresCompatibility) {
      compatibilityModifyCode(objClass)
    }
  }
  
  for (extension in allExtensions) {
    extensionCode(extension)
  }
  
  if (objClass.requiresCompatibility) {
    for (extension in allExtensions) {
      compatibilityExtensionWsCode(extension)
    }
  }
}

private fun CodeContext.extensionCode(extProperty: ExtProperty<*, *>) {
  val isChild = unwrapReferenceType(extProperty.valueType)?.child
  if (isChild == null) {
    notReferenceError("extension ws code", extProperty)
    return
  }
  val generic = if (extProperty.receiver.builderWithTypeParameter) "<out ${extProperty.receiver.javaFullName}>" else ""
  if (extProperty.annotations.any { it.fqName == Internal.decoded }) {
    annotation("get:$Internal")
    annotation("set:$Internal")
  }
  if (!isChild) {
    annotation(Parent.toString())
  }
  sectionNoBrackets("${explicitApiModifier}var ${extProperty.receiver.defaultJavaBuilderName}$generic.${extProperty.name}: ${
    getJavaBuilderTypeWithGeneric(extProperty)
  }") {
    line("by $WorkspaceEntity.extensionBuilder(${getEntityType(extProperty)}::class.java)")
  }
}
