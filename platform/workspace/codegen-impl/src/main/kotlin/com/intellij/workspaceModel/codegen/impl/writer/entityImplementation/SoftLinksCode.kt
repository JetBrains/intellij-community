// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityImplementation

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.OwnProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.CodeContext
import com.intellij.workspaceModel.codegen.impl.dsl.generateCode
import com.intellij.workspaceModel.codegen.impl.writer.SymbolicEntityId
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceMutableIndex
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.unwrapReferenceType
import com.intellij.workspaceModel.codegen.impl.writer.getAllProperties
import com.intellij.workspaceModel.codegen.impl.writer.getJavaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.referenceNameToSyntheticSymbolicIdFieldName
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdField
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdFieldName
import com.intellij.workspaceModel.codegen.impl.writer.toQualifiedName

internal fun CodeContext.softLinksCode(
  objClass: ObjClass<*>,
  hasSoftLinks: Boolean,
  referencesInSymbolicId: Set<OwnProperty<*, *>>,
) {
  if (!hasSoftLinks && referencesInSymbolicId.isEmpty()) return
  val relevantProperties =
    getAllProperties(objClass, withSymbolicId = false, withRefs = false, withEntitySource = false).filter { it.hasSoftLinks() }

  getLinksCode(relevantProperties, referencesInSymbolicId)

  indexCode(relevantProperties, referencesInSymbolicId)

  updateLinksIndexCode(relevantProperties, referencesInSymbolicId)

  updateLinkCode(relevantProperties, referencesInSymbolicId)
}

private fun CodeContext.getLinksCode(relevantProperties: List<OwnProperty<*, *>>, referencesInSymbolicId: Set<OwnProperty<*, *>>) {
  section("override fun getLinks(): Set<${SymbolicEntityId}<*>>") {
    line("val result = HashSet<${SymbolicEntityId}<*>>()")
    for (property in relevantProperties) {
      operate(property.valueType, property.name, true) {
        line("result.add($it)")
      }
    }
    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      line("result.add($syntheticName)")
    }
    line("return result")
  }
}

private fun CodeContext.indexCode(relevantProperties: List<OwnProperty<*, *>>, referencesInSymbolicId: Set<OwnProperty<*, *>>) {
  section("override fun index(index: ${WorkspaceMutableIndex}<${SymbolicEntityId}<*>>)") {
    for (property in relevantProperties) {
      operate(property.valueType, property.name, true) {
        line("index.index(this, $it)")
      }
    }
    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      line("index.index(this, $syntheticName)")
    }
  }
}

private fun CodeContext.updateLinksIndexCode(relevantProperties: List<OwnProperty<*, *>>, referencesInSymbolicId: Set<OwnProperty<*, *>>) {
  section("override fun updateLinksIndex(prev: Set<${SymbolicEntityId}<*>>, index: ${WorkspaceMutableIndex}<${SymbolicEntityId}<*>>)") {
    // TODO verify logic
    line("val mutablePreviousSet = HashSet(prev)")
    for (property in relevantProperties) {
      operate(property.valueType, property.name, true) {
        val cleanName = it.clean()
        line("val removedItem_$cleanName = mutablePreviousSet.remove($it)")
        section("if (!removedItem_$cleanName)") {
          line("index.index(this, $it)")
        }
      }
    }
    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      val cleanSyntheticName = syntheticName.clean()
      line("val removedItem_$cleanSyntheticName = mutablePreviousSet.remove($syntheticName)")
      section("if (!removedItem_$cleanSyntheticName)") {
        line("index.index(this, $syntheticName)")
      }
    }
    section("for (removed in mutablePreviousSet)") {
      line("index.remove(this, removed)")
    }
  }
}

private fun CodeContext.updateLinkCode(relevantProperties: List<OwnProperty<*, *>>, referencesInSymbolicId: Set<OwnProperty<*, *>>) {
  section("override fun updateLink(oldLink: ${SymbolicEntityId}<*>, newLink: ${SymbolicEntityId}<*>): Boolean") {
    line("var changed = false")
    for (property in relevantProperties) {
      val returnValue = processType(property.valueType, property.name)
      if (returnValue != null) {
        `if`("$returnValue != null") {
          if (property.valueType is ValueType.Collection<*, *> && !property.valueType.isReferenceType()) {
            line("${property.name} = $returnValue as ${getJavaMutableType(property)}")
          }
          else {
            line("${property.name} = $returnValue")
          }
        }
      }
    }
    for (reference in referencesInSymbolicId) {
      val syntheticName = referenceNameToSyntheticSymbolicIdFieldName(reference.name)
      val refSymbolicId = unwrapReferenceType(reference.valueType)?.target?.symbolicIdField
      if (refSymbolicId == null) {
        reportPropertyError("Cannot find symbolic id of the referenced entity", reference)
        return@section
      }
      val returnValue = processType(refSymbolicId.valueType, syntheticName)
      if (returnValue != null) {
        `if`("$returnValue != null") {
          if (refSymbolicId.valueType is ValueType.Set<*> && !refSymbolicId.valueType.isReferenceType()) {
            line("$syntheticName = $returnValue as ${getJavaMutableType(refSymbolicId)}")
          }
          else if (refSymbolicId.valueType is ValueType.List<*> && !refSymbolicId.valueType.isReferenceType()) {
            line("$syntheticName = $returnValue as ${getJavaMutableType(refSymbolicId)}")
          }
          else {
            line("$syntheticName = $returnValue")
          }
        }
      }
    }
    line("return changed")
  }
}

internal fun ObjClass<*>.hasSoftLinks(): Boolean {
  return getAllProperties(this, withSymbolicId = false, withRefs = false, withEntitySource = false).any { field ->
    field.hasSoftLinks()
  }
}

private fun ObjProperty<*, *>.hasSoftLinks(): Boolean {
  return name != symbolicIdFieldName && valueType.hasSoftLinks()
}

private fun ValueType<*>.hasSoftLinks(): Boolean = when (this) {
  is ValueType.Blob -> isSymbolicId
  is ValueType.Collection<*, *> -> elementType.hasSoftLinks()
  is ValueType.Optional<*> -> type.hasSoftLinks()
  is ValueType.AbstractClass<*> -> isSymbolicId || subclasses.any { it.hasSoftLinks() }
  is ValueType.FinalClass<*> -> isSymbolicId || properties.any { it.valueType.hasSoftLinks() }
  else -> false
}

private val ValueType.JvmClass<*>.isSymbolicId: Boolean
  get() = SymbolicEntityId.decoded in javaSuperClasses

private fun CodeContext.operate(
  valueType: ValueType<*>,
  varName: String,
  generateNewName: Boolean,
  operation: CodeContext.(String) -> Unit,
) {
  when (valueType) {
    is ValueType.JvmClass -> {
      when {
        valueType.isSymbolicId -> operation(varName)
        valueType is ValueType.AbstractClass<*> -> processAbstractClass(valueType, varName, operation, generateNewName)
        valueType is ValueType.FinalClass<*> -> processFinalClassProperties(varName, valueType.properties, operation)
      }
    }
    is ValueType.Collection<*, *> -> {
      val elementType = valueType.elementType
      // TODO: check a collection of collections
      section("for (item in ${varName})") {
        operate(elementType, "item", false, operation)
      }
    }
    is ValueType.Optional<*> -> {
      if (valueType.type is ValueType.JvmClass && (valueType.type as ValueType.JvmClass<*>).isSymbolicId) {
        val newVarName = "optionalLink_${varName.clean()}"
        line("val $newVarName = $varName")
        `if`("$newVarName != null") {
          operate(valueType.type, newVarName, true, operation)
        }
      }
    }
    else -> {}
  }
}

private fun CodeContext.processFinalClassProperties(
  varName: String,
  classProperties: List<ValueType.ClassProperty<*>>,
  operation: CodeContext.(String) -> Unit,
) {
  for ((name, valueType) in classProperties) {
    operate(valueType, "$varName.$name", true, operation)
  }
}

private fun CodeContext.processAbstractClass(
  thisClass: ValueType.AbstractClass<*>,
  varName: String,
  operation: CodeContext.(String) -> Unit,
  generateNewName: Boolean = true,
) {
  val newVarName = if (generateNewName) "_${varName.clean()}" else varName
  if (generateNewName) line("val $newVarName = $varName")
  section("when ($newVarName)") {
    for (subclass in thisClass.subclasses) {
      section("is ${subclass.kotlinClassName.toQualifiedName()} -> ") {
        if (subclass is ValueType.AbstractClass) {
          processAbstractClass(subclass, newVarName, operation, generateNewName)
        }
        else if (subclass is ValueType.FinalClass) {
          processFinalClassProperties(newVarName, subclass.properties, operation)
        }
      }
    }
  }
}

private fun CodeContext.processType(valueType: ValueType<*>, varName: String): String? {
  return when (valueType) {
    is ValueType.JvmClass -> {
      when {
        valueType.isSymbolicId -> {
          val name = "${varName.clean()}_data"
          lineNoNl("val $name = ")
          ifElse("$varName == oldLink", {
            line("changed = true")
            line("newLink as ${valueType.kotlinClassName.toQualifiedName()}")
          }) { line("null") }
          name
        }
        valueType is ValueType.AbstractClass<*> -> {
          processAbstractClass(valueType, varName)
        }
        valueType is ValueType.FinalClass<*> -> {
          val updates = valueType.properties.mapNotNull {
            val returnValue = processType(it.valueType, "$varName.${it.name}")
            if (returnValue != null) it.name to returnValue else null
          }
          if (updates.isEmpty()) {
            null
          }
          else {
            val name = "${varName.clean()}_data"
            line("var $name = $varName")
            updates.forEach { (fieldName, update) ->
              `if`("$update != null") {
                line("$name = $name.copy($fieldName = $update)")
              }
            }
            name
          }
        }
        else -> null
      }
    }
    is ValueType.Collection<*, *> -> {
      var name: String? = "${varName.clean()}_data"
      val builder = generateCode {
        section("val $name = $varName.map") label@{
          val returnValue = processType(valueType.elementType, "it")
          if (returnValue != null) {
            ifElse("$returnValue != null", {
              line(returnValue)
            }) { line("it") }
          }
          else {
            name = null
          }
        }
      }
      if (name != null) {
        line(builder)
      }
      name
    }
    is ValueType.Optional<*> -> {
      var name: String? = "${varName.clean()}_data_optional"
      val builder = generateCode {
        lineNoNl("var $name = ")
        ifElse("$varName != null", labelIf@{
          val returnValue = processType(valueType.type, "$varName!!")
          if (returnValue != null) {
            line(returnValue)
          }
          else {
            name = null
          }
        }) { line("null") }
      }
      if (name != null) {
        line(builder)
      }
      name
    }
    else -> null
  }
}

private fun CodeContext.processAbstractClass(thisClass: ValueType.AbstractClass<*>, varName: String): String {
  val newVarName = "_${varName.clean()}"
  val resVarName = "res_${varName.clean()}"
  line("val $newVarName = $varName")
  lineNoNl("val $resVarName = ")
  section("when ($newVarName)") {
    for (subclass in thisClass.subclasses) {
      section("is ${subclass.kotlinClassName.toQualifiedName()} -> ") label@{
        var sectionVarName = newVarName
        val properties: List<ValueType.ClassProperty<*>> =
          if (subclass is ValueType.FinalClass) {
            subclass.properties
          }
          else {
            if (subclass is ValueType.AbstractClass) sectionVarName = processAbstractClass(subclass, sectionVarName)
            emptyList()
          }
        val updates = properties.mapNotNull {
          val returnValue = processType(it.valueType, "$sectionVarName.${it.name}")
          if (returnValue != null) it.name to returnValue else null
        }
        if (updates.isEmpty()) {
          line(sectionVarName)
        }
        else {
          val name = "${sectionVarName.clean()}_data"
          line("var $name = $sectionVarName")
          updates.forEach { (fieldName, update) ->
            `if`("$update != null") {
              line("$name = $name.copy($fieldName = $update)")
            }
          }
          line(name)
        }
      }
    }
  }
  return resVarName
}

private fun String.clean(): String {
  return this.replace(".", "_").replace('!', '_')
}