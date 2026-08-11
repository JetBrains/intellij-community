package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.isEntityWithSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allSuperClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields

internal fun GeneratorContext.checkSuperTypes(objClass: ObjClass<*>) {
  objClass.superTypes.filterIsInstance<ObjClass<*>>().forEach { superClass ->
    if (!superClass.openness.extendable) {
      reportClassError("Class '${superClass.name}' cannot be extended", objClass)
    }
    else if (!superClass.openness.openHierarchy && superClass.module != objClass.module) {
      reportClassError("Class '${superClass.name}' cannot be extended from other modules", objClass)
    }
  }
}

internal fun GeneratorContext.checkSymbolicId(objClass: ObjClass<*>) {
  if (!objClass.isEntityWithSymbolicId) return
  if (objClass.openness == ObjClass.Openness.abstract) return
  if (objClass.fields.none { it.name == symbolicIdFieldName }) {
    reportClassError("Class extends '${WorkspaceEntityWithSymbolicId.simpleName}' but " +
                     "doesn't override 'WorkspaceEntityWithSymbolicId.getSymbolicId' property", objClass)
  }
}

internal fun GeneratorContext.checkProperty(objProperty: ObjProperty<*, *>) {
  checkInheritance(objProperty)
  checkImmutable(objProperty)
  checkPropertyType(objProperty)
}

private fun GeneratorContext.checkImmutable(objProperty: ObjProperty<*, *>) {
  if (objProperty.mutable) {
    reportPropertyError("An immutable interface can't contain mutable properties", objProperty)
  }
}

private fun GeneratorContext.checkPropertyType(objProperty: ObjProperty<*, *>) {
  val errorMessage = when (val type = objProperty.valueType) {
    is ValueType.ObjRef<*> -> {
      if (type.child) "Child references should always be nullable"
      else null
    }

    else -> checkType(type)
  }
  if (errorMessage != null) {
    reportPropertyError(errorMessage, objProperty)
  }
}

private fun checkType(type: ValueType<*>): String? = when (type) {
  is ValueType.Optional -> when (type.type) {
    is ValueType.List<*> -> "Optional lists aren't supported"
    is ValueType.Set<*> -> "Optional sets aren't supported"
    else -> checkType(type.type)
  }

  is ValueType.Set<*> -> {
    if (type.elementType.isReferenceType()) {
      "Set of references isn't supported"
    }
    else checkType(type.elementType)
  }

  is ValueType.Map<*, *> -> {
    checkType(type.keyType) ?: checkType(type.valueType)
  }

  else -> null
}

private fun GeneratorContext.checkInheritance(objProperty: ObjProperty<*, *>) {
  objProperty.receiver.allSuperClasses.mapNotNull { it.fieldsByName[objProperty.name] }.forEach { overriddenField ->
    if (!overriddenField.open) {
      reportPropertyError("Property '${overriddenField.receiver.name}::${overriddenField.name}' cannot be overridden", objProperty)
    }
  }
}

internal fun GeneratorContext.checkExtensionFields(module: CompiledObjModule) {
  module.extensions.forEach { extProperty ->
    if (!extProperty.valueType.isReferenceType()) {
      reportPropertyError("Extension property is supposed to be a reference to another entity only.", extProperty)
    }
  }
}

// TODO
fun GeneratorContext.checkReference(referenceField: ObjProperty<*, *>) {
  fun fail(message: String) = reportPropertyError(message, referenceField)

  val receiver = referenceField.receiver
  val referenceTarget = unwrapReferenceType(referenceField.valueType)!!.target
  val allExtensions = setOf(referenceTarget.module, receiver.module).flatMap { it.extensions }

  val otherReference =
    referenceTarget.refsFields.filter { unwrapReferenceType(it.valueType)!!.target == receiver && it != referenceField } +
    allExtensions.filter { unwrapReferenceType(it.valueType)!!.target == receiver && it.receiver == referenceTarget && it != referenceField }
  if (otherReference.isEmpty()) {
    fail("""
      |Reference should be declared at both entities. It exist at ${receiver.name}#${referenceField.name}, but is absent at ${referenceTarget.name}.
      | Probably missing `val ${referenceTarget.name}.missingReference: ${receiver.name} by WorkspaceEntity.extension()`
    """.trimMargin())
    return
  }
  if (otherReference.size > 1) {
    fail("""
        |More then one reference to ${receiver.name} declared: 
        |${otherReference[0].receiver.name}#${otherReference[0].name}
        |${otherReference[1].receiver.name}#${otherReference[1].name}
        |""".trimMargin())
    return
  }
  val referencedField = otherReference[0]
  if (unwrapReferenceType(referenceField.valueType)!!.child == unwrapReferenceType(referencedField.valueType)!!.child) {
    val (childStr, fix) = if (unwrapReferenceType(referenceField.valueType)!!.child) {
      "child" to "Probably @Parent annotation is missing from one of the properties."
    }
    else {
      "parent" to "Probably both properties are annotated with @Parent, while only one should be."
    }
    fail("Both fields ${receiver.name}#${referenceField.name} and ${referenceTarget.name}#${referencedField.name} are marked as $childStr. $fix")
  }
}

fun GeneratorContext.checkReferences(objClass: ObjClass<*>) {
  for (referenceField in getAllReferenceProperties(objClass)) {
    this.checkReference(referenceField)
  }
}