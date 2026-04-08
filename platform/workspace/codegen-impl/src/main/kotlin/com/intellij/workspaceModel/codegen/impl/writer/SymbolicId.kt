package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.impl.writer.classes.getFirstMatch
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType

internal val ObjClass<*>.symbolicIdField: OwnProperty<*, *>?
  get() {
    return allFields.singleOrNull { it.name == symbolicIdFieldName }
  }

fun ObjClass<*>.referencesInSymbolicId(): Set<OwnProperty<*, *>>? {
  val regexForSymbolicIdExpression = Regex("=? ?[A-z]+\\((.*)\\)")
  val regexForSymbolicIdReference = Regex("([A-z_0-9]*)\\.symbolicId")

  val expression = (symbolicIdField?.valueKind as? ObjProperty.ValueKind.Computable)?.expression
  if (expression == null || expression == "null") return null
  val parts = regexForSymbolicIdExpression.getFirstMatch(expression)?.split(",")?.map { it.trim() } ?: return null
  val references = parts.mapNotNull { regexForSymbolicIdReference.getFirstMatch(it) }.toSet()
  if (references.isEmpty()) return null

  val referencesInUse = allFields.filter { it.name in references }.toSet()
  // TODO: sanity check that for every reference we have found a property
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

private fun symbolicIdPropertyToRepresentation(property: OwnProperty<*, *>): SymbolicIdRepresentation {
  val symbolicIdType = property.valueType.javaType
  val expression = (property.valueKind as ObjProperty.ValueKind.Computable).expression.trim()
  val args = expression.dropWhile { it != '(' }.drop(1).dropLast(1)
    .split(",").map { it.trim() }
  return SymbolicIdRepresentation(javaType = symbolicIdType, args = args)
}

fun LinesBuilder.symbolicIdReferenceCode(field: ObjProperty<*, *>) {
  val referencesInSymbolicId = field.receiver.referencesInSymbolicId() ?: return
  val referenceInSymbolicId = referencesInSymbolicId.find { it.name == field.name }
  if (referenceInSymbolicId != null) {
    val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(field.name)
    val newRefSymbolicIdValue =
      symbolicIdPropertyToRepresentation(referenceInSymbolicId.valueType.getRefType().target.symbolicIdField!!)
        .constructorUsingReceiver("value")
    line("getEntityData(true).${syntheticName} = $newRefSymbolicIdValue")
    line("changedProperty.add(\"${syntheticName}\")")
  }
}

fun ObjClass<*>.symbolicIdImplCode(): String {
  val theProperty = symbolicIdField ?: return ""
  val referencesInSymbolicId = referencesInSymbolicId()
  if (referencesInSymbolicId.isNullOrEmpty())
    return "override val symbolicId: ${theProperty.valueType.javaType} = super.symbolicId\n"
  val referencesNamesInSymbolicId = referencesInSymbolicId.map { it.name }.toSet()
  val symbolicIdImpl = symbolicIdPropertyToRepresentation(theProperty).constructorUsingReceiver("dataSource") {
    val receiver = it.split(".").firstOrNull()
    if (receiver == null || receiver !in referencesNamesInSymbolicId) it
    else referenceNameToSyntheticSymbolicIdFieldName(receiver)
  }
  return "override val symbolicId: ${theProperty.valueType.javaType} = $symbolicIdImpl\n"
}

fun LinesBuilder.symbolicIdIsInitializedCode(objClass: ObjClass<*>) {
  val referencesInSymbolicId = objClass.referencesInSymbolicId()
  if (referencesInSymbolicId.isNullOrEmpty()) {
    return
  } 
  for (reference in referencesInSymbolicId) {
    val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
    val capitalizedSyntheticName = syntheticName.replaceFirstChar { it.titlecaseChar() }
    section("if (!getEntityData().is${capitalizedSyntheticName}Initialized())") {
      line("error(\"Field ${objClass.name}#${reference.name} should be initialized\")")
    }
  }
}