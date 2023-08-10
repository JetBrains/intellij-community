// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaMutableType
import com.intellij.workspaceModel.codegen.impl.writer.*

internal fun ObjClass<*>.softLinksCode(context: LinesBuilder, hasSoftLinks: Boolean) {
  context.conditionalLine({ hasSoftLinks }, "override fun getLinks(): Set<${SymbolicEntityId}<*>>") {
    line("val result = HashSet<${SymbolicEntityId}<*>>()")
    operate(this) { line("result.add($it)") }
    line("return result")
  }

  context.conditionalLine(
    { hasSoftLinks },
    "override fun index(index: ${WorkspaceMutableIndex}<${SymbolicEntityId}<*>>)"
  ) {
    operate(this) { line("index.index(this, $it)") }
  }

  context.conditionalLine(
    { hasSoftLinks },
    "override fun updateLinksIndex(prev: Set<${SymbolicEntityId}<*>>, index: ${WorkspaceMutableIndex}<${SymbolicEntityId}<*>>)"
  ) {
    line("// TODO verify logic")
    line("val mutablePreviousSet = HashSet(prev)")
    operate(this) {
      line("val removedItem_${it.clean()} = mutablePreviousSet.remove($it)")
      section("if (!removedItem_${it.clean()})") {
        line("index.index(this, $it)")
      }
    }
    section("for (removed in mutablePreviousSet)") {
      line("index.remove(this, removed)")
    }
  }

  context.conditionalLine(
    { hasSoftLinks },
    "override fun updateLink(oldLink: ${SymbolicEntityId}<*>, newLink: ${SymbolicEntityId}<*>): Boolean"
  ) {
    line("var changed = false")
    operateUpdateLink(this)
    line("return changed")
  }
}

internal fun ObjClass<*>.hasSoftLinks(): Boolean {
  return fields.noSymbolicId().noRefs().any { field ->
    field.hasSoftLinks()
  }
}

internal fun ObjProperty<*, *>.hasSoftLinks(): Boolean {
  if (name == "symbolicId") return false
  return valueType.hasSoftLinks()
}

internal fun ValueType<*>.hasSoftLinks(): Boolean = when (this) {
  is ValueType.Blob -> isSymbolicId
  is ValueType.Collection<*, *> -> elementType.hasSoftLinks()
  is ValueType.Optional<*> -> type.hasSoftLinks()
  is ValueType.SealedClass<*> -> isSymbolicId || subclasses.any { it.hasSoftLinks() }
  is ValueType.DataClass<*> -> isSymbolicId || properties.any { it.type.hasSoftLinks() }
  else -> false
}

val ValueType.JvmClass<*>.isSymbolicId: Boolean
  get() = SymbolicEntityId.decoded in javaSuperClasses

private fun ObjClass<*>.operate(
  context: LinesBuilder,
  operation: LinesBuilder.(String) -> Unit
) {
  allFields.noSymbolicId().noRefs().forEach { field ->
    field.valueType.operate(field.name, context, operation)
  }
}

private fun ValueType<*>.operate(
  varName: String,
  context: LinesBuilder,
  operation: LinesBuilder.(String) -> Unit,
  generateNewName: Boolean = true,
) {
  when (this) {
    is ValueType.JvmClass -> {
      when {
        isSymbolicId -> context.operation(varName)
        this is ValueType.SealedClass<*> -> processSealedClass(this, varName, context, operation, generateNewName)
        this is ValueType.DataClass<*> -> processDataClassProperties(varName, context, properties, operation)
      }
    }
    is ValueType.Collection<*, *> -> {
      val elementType = elementType

      context.section("for (item in ${varName})") {
        elementType.operate("item", this@section, operation, false)
      }
    }
    is ValueType.Optional<*> -> {
      if (type is ValueType.JvmClass && (type as ValueType.JvmClass<*>).isSymbolicId) {
        context.line("val optionalLink_${varName.clean()} = $varName")
        context.`if`("optionalLink_${varName.clean()} != null") label@{
          type.operate("optionalLink_${varName.clean()}", this@label, operation)
        }
      }
    }
    else -> Unit
  }
}

private fun processDataClassProperties(varName: String,
                                       context: LinesBuilder,
                                       dataClassProperties: List<ValueType.DataClassProperty>,
                                       operation: LinesBuilder.(String) -> Unit) {
  for (property in dataClassProperties) {
    property.type.operate("$varName.${property.name}", context, operation)
  }
}

private fun processSealedClass(thisClass: ValueType.SealedClass<*>,
                               varName: String,
                               context: LinesBuilder,
                               operation: LinesBuilder.(String) -> Unit,
                               generateNewName: Boolean = true) {
  val newVarName = if (generateNewName) "_${varName.clean()}" else varName
  if (generateNewName) context.line("val $newVarName = $varName")
  context.section("when ($newVarName)") {
    listBuilder(thisClass.subclasses) { item ->
      val linesBuilder = LinesBuilder(StringBuilder(), context.indentLevel+1, context.indentSize).wrapper()
      if (item is ValueType.SealedClass) {
        processSealedClass(item, newVarName, linesBuilder, operation, generateNewName)
      }
      else if (item is ValueType.DataClass) {
        processDataClassProperties(newVarName, linesBuilder, item.properties, operation)
      }
      section("is ${item.javaClassName.toQualifiedName()} -> ") {
        result.append(linesBuilder.result)
      }
    }
  }
}


private fun ObjClass<*>.operateUpdateLink(context: LinesBuilder) {
  allFields.noSymbolicId().noRefs().forEach { field ->
    val retType = field.valueType.processType(context, field.name)
    if (retType != null) {
      context.`if`("$retType != null") {
        if (field.valueType is ValueType.Set<*> && !field.valueType.isRefType()) {
          line("${field.name} = $retType as ${field.valueType.javaMutableType}")
        } else if (field.valueType is ValueType.List<*> && !field.valueType.isRefType()) {
          line("${field.name} = $retType as ${field.valueType.javaMutableType}")
        } else {
          line("${field.name} = $retType")
        }
      }
    }
  }
}

private fun ValueType<*>.processType(
  context: LinesBuilder,
  varName: String,
): String? {
  return when (this) {
    is ValueType.JvmClass -> {
      when {
        isSymbolicId -> {
          val name = "${varName.clean()}_data"
          context.lineNoNl("val $name = ")
          context.ifElse("$varName == oldLink", {
            line("changed = true")
            line("newLink as ${javaClassName.toQualifiedName()}")
          }) { line("null") }
          name
        }
        this is ValueType.SealedClass<*> -> {
          processSealedClass(this, varName, context)
        }
        this is ValueType.DataClass<*> -> {
          val updates = properties.mapNotNull label@{
            val retVar = it.type.processType(
              context,
              "$varName.${it.name}"
            )
            if (retVar != null) it.name to retVar else null
          }
          if (updates.isEmpty()) {
            null
          }
          else {
            val name = "${varName.clean()}_data"
            context.line("var $name = $varName")
            updates.forEach { (fieldName, update) ->
              context.`if`("$update != null") {
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
      val builder = lines(context.indentLevel) {
        section("val $name = $varName.map") label@{
          val returnVar = elementType.processType(
            this@label,
            "it"
          )
          if (returnVar != null) {
            ifElse("$returnVar != null", {
              line(returnVar)
            }) { line("it") }
          }
          else {
            name = null
          }
        }
      }
      if (name != null) {
        context.result.append(builder)
      }
      name
    }
    is ValueType.Optional<*> -> {
      var name: String? = "${varName.clean()}_data_optional"
      val builder = lines(context.indentLevel) {
        lineNoNl("var $name = ")
        ifElse("$varName != null", labelIf@{
          val returnVar = type.processType(
            this@labelIf,
            "$varName!!"
          )
          if (returnVar != null) {
            line(returnVar)
          }
          else {
            name = null
          }
        }) { line("null") }
      }
      if (name != null) {
        context.result.append(builder)
      }
      name
    }
    else -> return null
  }
}

private fun processSealedClass(thisClass: ValueType.SealedClass<*>,
                               varName: String,
                               context: LinesBuilder): String {
  val newVarName = "_${varName.clean()}"
  val resVarName = "res_${varName.clean()}"
  context.line("val $newVarName = $varName")
  context.lineNoNl("val $resVarName = ")
  context.section("when ($newVarName)") {
    listBuilder(thisClass.subclasses) { item ->
      section("is ${item.javaClassName.toQualifiedName()} -> ") label@{
        var sectionVarName = newVarName
        val properties: List<ValueType.DataClassProperty>
        if (item is ValueType.SealedClass) {
          sectionVarName = processSealedClass(item, sectionVarName, this)
          properties = emptyList()
        }
        else if (item is ValueType.DataClass) {
          properties = item.properties
        }
        else {
          properties = emptyList()
        }
        val updates = properties.mapNotNull {
          val retVar = it.type.processType(
            this@label,
            "$sectionVarName.${it.name}"
          )
          if (retVar != null) it.name to retVar else null
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