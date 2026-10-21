package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.getFirstMatch
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.`if`
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName

internal val ObjClass<*>.symbolicIdProperty: OwnProperty<*, *>?
  get() {
    return getAllProperties(this).singleOrNull { it.name == symbolicIdPropertyName }
  }

fun GeneratorContext.referencesInSymbolicId(objClass: ObjClass<*>): Set<OwnProperty<*, *>>? {
  val regexForSymbolicIdExpression = Regex("=? ?[A-z]+\\((.*)\\)")
  val regexForSymbolicIdReference = Regex("([A-z_0-9]*)\\.symbolicId")

  val symbolicIdField = objClass.symbolicIdProperty ?: return null
  val expression = (symbolicIdField.valueKind as? ObjProperty.ValueKind.Computable)?.expression
  if (expression == null || expression == "null") return null
  val parts = regexForSymbolicIdExpression.getFirstMatch(expression)?.split(",")?.map { it.trim() } ?: return null
  val references = parts.mapNotNull { regexForSymbolicIdReference.getFirstMatch(it) }.toSet()
  if (references.isEmpty()) return null

  val referencesInUse = mutableSetOf<OwnProperty<*, *>>()
  for (reference in references) {
    val referenceField = getAllProperties(objClass).singleOrNull { it.name == reference }
    if (referenceField == null) {
      reportPropertyError("Cannot find property $reference referenced in symbolicId", symbolicIdField)
      return null
    }
    referencesInUse.add(referenceField)
  }

  return referencesInUse
}

fun referenceNameToSyntheticSymbolicIdFieldName(referenceName: String): String {
  return "${referenceName}SymbolicId_Synthetic"
}

private data class SymbolicIdRepresentation(val javaType: QualifiedName, val args: List<String>) {
  fun constructorUsingReceiver(receiver: String, transform: (String) -> String = { it }): String {
    val argsList = args.joinToString(prefix = "(", postfix = ")", separator = ", ") { "$receiver.${transform(it)}" }
    return "$javaType$argsList"
  }
}

private fun isModuleEntitySymbolicId(property: ObjProperty<*, *>): Boolean {
  return property.receiver.name == "ModuleEntity" && property.receiver.module.name == "com.intellij.platform.workspace.jps.entities"
}

private fun GeneratorContext.symbolicIdPropertyToRepresentation(property: OwnProperty<*, *>): SymbolicIdRepresentation? {
  val symbolicIdType = getJavaType(property)
  if (isModuleEntitySymbolicId(property)) {
    return SymbolicIdRepresentation(javaType = symbolicIdType, args = listOf("name"))
  }
  val propertyAsComputable = property.valueKind as? ObjProperty.ValueKind.Computable ?: run {
    val errorMessage = "${property.receiver.name}#${property.name} is expected to be a computable symbolicId"
    reportPropertyError(errorMessage, property)
    return null
  }
  val expression = propertyAsComputable.expression.trim()
  val args = expression.dropWhile { it != '(' }.drop(1).dropLast(1)
    .split(",").map { it.trim() }
  return SymbolicIdRepresentation(javaType = symbolicIdType, args = args)
}

fun CodeContext.symbolicIdImplementationCode(objClass: ObjClass<*>) {
  val theProperty = objClass.symbolicIdProperty ?: return
  val referencesInSymbolicId = referencesInSymbolicId(objClass)
  if (referencesInSymbolicId.isNullOrEmpty()) {
    +"override val symbolicId: ${getJavaType(theProperty)} = super.symbolicId"
    return
  }
  val referencesNamesInSymbolicId = referencesInSymbolicId.map { it.name }.toSet()
  val symbolicIdImpl = symbolicIdPropertyToRepresentation(theProperty)?.constructorUsingReceiver("dataSource") {
    val receiver = it.split(".").firstOrNull()
    if (receiver == null || receiver !in referencesNamesInSymbolicId) it
    else referenceNameToSyntheticSymbolicIdFieldName(receiver)
  } ?: return
  +"override val symbolicId: ${getJavaType(theProperty)} = $symbolicIdImpl"
}

fun CodeContext.symbolicIdIsInitializedCode(objClass: ObjClass<*>) {
  val referencesInSymbolicId = referencesInSymbolicId(objClass)
  if (referencesInSymbolicId.isNullOrEmpty()) {
    return
  }
  for (reference in referencesInSymbolicId) {
    val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
    val capitalizedSyntheticName = syntheticName.replaceFirstChar { it.titlecaseChar() }
    `if`("!getEntityData().is${capitalizedSyntheticName}Initialized()") {
      +"error(\"Field ${objClass.name}#${reference.name} should be initialized\")"
    }
  }
}

fun CodeContext.symbolicIdBuilderCode(objClass: ObjClass<*>) {
  val property = objClass.symbolicIdProperty ?: return
  val expression = (property.valueKind as? ObjProperty.ValueKind.Computable)?.expression ?: return
  if (expression == "null") return
  val returnType = getJavaType(property)
  +"${explicitApiModifier}val ${property.javaName}: $returnType"
  +"get() = $expression"
}