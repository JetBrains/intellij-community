package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.classes.isEntityWithSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allRefsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allSuperClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields

internal fun checkSuperTypes(objClass: ObjClass<*>, reporter: ProblemReporter) {
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

internal fun checkSymbolicId(objClass: ObjClass<*>, reporter: ProblemReporter) {
  if (!objClass.isEntityWithSymbolicId) return
  if (objClass.openness == ObjClass.Openness.abstract) return
  if (objClass.fields.none { it.name == symbolicIdFieldName }) {
    reporter.reportProblem(GenerationProblem("Class extends '${WorkspaceEntityWithSymbolicId.simpleName}' but " +
                                             "doesn't override 'WorkspaceEntityWithSymbolicId.getSymbolicId' property",
                                             GenerationProblem.Level.ERROR, ProblemLocation.Class(objClass)))
  }
}

internal fun checkProperty(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  checkInheritance(objProperty, reporter)
  checkAllImmutable(objProperty, reporter)
  checkPropertyType(objProperty, reporter)
}

private fun checkAllImmutable(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  if (objProperty.mutable) {
    reporter.reportProblem(GenerationProblem("An immutable interface can't contain mutable properties",
                                             GenerationProblem.Level.ERROR,
                                             ProblemLocation.Property(objProperty)))
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

private fun checkInheritance(objProperty: ObjProperty<*, *>, reporter: ProblemReporter) {
  objProperty.receiver.allSuperClasses.mapNotNull { it.fieldsByName[objProperty.name] }.forEach { overriddenField ->
    if (!overriddenField.open) {
      reporter.reportProblem(
        GenerationProblem("Property '${overriddenField.receiver.name}::${overriddenField.name}' cannot be overridden",
                          GenerationProblem.Level.ERROR, ProblemLocation.Property(objProperty)))
    }
  }
}

internal fun checkExtensionFields(module: CompiledObjModule, reporter: ProblemReporter) {
  module.extensions.forEach { extProperty ->
    if (!extProperty.valueType.isRefType()) {
      reporter.reportProblem(GenerationProblem("Extension property is supposed to be a reference to another entity only.",
                                               GenerationProblem.Level.ERROR, ProblemLocation.Property(extProperty)))
    }
  }
}

fun checkReference(referenceField: ObjProperty<*, *>, reporter: ProblemReporter) {
  fun fail(message: String) =
    reporter.reportProblem(GenerationProblem(message, GenerationProblem.Level.ERROR, ProblemLocation.Property(referenceField)))

  val receiver = referenceField.receiver
  val referenceTarget = referenceField.valueType.getRefType().target
  val allExtensions = setOf(referenceTarget.module, receiver.module).flatMap { it.extensions }

  val otherReference =
    referenceTarget.refsFields.filter { it.valueType.getRefType().target == receiver && it != referenceField } +
    allExtensions.filter { it.valueType.getRefType().target == receiver && it.receiver == referenceTarget && it != referenceField }
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
  if (referenceField.valueType.getRefType().child == referencedField.valueType.getRefType().child) {
    val (childStr, fix) = if (referenceField.valueType.getRefType().child) {
      "child" to "Probably @Parent annotation is missing from one of the properties."
    }
    else {
      "parent" to "Probably both properties are annotated with @Parent, while only one should be."
    }
    fail("Both fields ${receiver.name}#${referenceField.name} and ${referenceTarget.name}#${referencedField.name} are marked as $childStr. $fix")
  }
}

fun checkReferences(objClass: ObjClass<*>, reporter: ProblemReporter) {
  for (referenceField in objClass.allRefsFields) {
    checkReference(referenceField, reporter)
  }
}