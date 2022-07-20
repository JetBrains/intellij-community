// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.javaSimpleName
import com.intellij.workspaceModel.codegen.javaSuperType
import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.WsData
import com.intellij.workspaceModel.codegen.deft.model.WsSealed
import com.intellij.workspaceModel.codegen.utils.LinesBuilder
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import com.intellij.workspaceModel.codegen.deft.*
import com.intellij.workspaceModel.codegen.deft.Field
import java.lang.StringBuilder

internal fun ObjType<*, *>.softLinksCode(context: LinesBuilder, hasSoftLinks: Boolean, simpleTypes: List<DefType>) {
  context.conditionalLine({ hasSoftLinks }, "override fun getLinks(): Set<${PersistentEntityId::class.fqn}<*>>") {
    line("val result = HashSet<${PersistentEntityId::class.fqn}<*>>()")
    operate(this@conditionalLine, simpleTypes) { line("result.add($it)") }
    line("return result")
  }

  context.conditionalLine(
    { hasSoftLinks },
    "override fun index(index: ${WorkspaceMutableIndex::class.fqn}<${PersistentEntityId::class.fqn}<*>>)"
  ) {
    operate(this@conditionalLine, simpleTypes) { line("index.index(this, $it)") }
  }

  context.conditionalLine(
    { hasSoftLinks },
    "override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: ${WorkspaceMutableIndex::class.fqn}<${PersistentEntityId::class.fqn}<*>>)"
  ) {
    line("// TODO verify logic")
    line("val mutablePreviousSet = HashSet(prev)")
    operate(this@conditionalLine, simpleTypes) {
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
    "override fun updateLink(oldLink: ${PersistentEntityId::class.fqn}<*>, newLink: ${PersistentEntityId::class.fqn}<*>): Boolean"
  ) {
    line("var changed = false")
    operateUpdateLink(this@conditionalLine, simpleTypes)
    line("return changed")
  }
}

internal fun ObjType<*, *>.hasSoftLinks(simpleTypes: List<DefType>): Boolean {
  return structure.declaredFields.noPersistentId().noRefs().any { field ->
    field.hasSoftLinks(simpleTypes)
  }
}

internal fun Field<*, *>.hasSoftLinks(simpleTypes: List<DefType>): Boolean {
  if (name == "persistentId") return false

  return when (type) {
    is TBlob<*> -> {
      return type.hasSoftLinks(simpleTypes)
    }
    is TCollection<*, *> -> {
      val elementType = type.elementType
      if (elementType is TBlob<*> && elementType.isPersistentId(simpleTypes)) {
        true
      }
      else {
        val elementType = type.elementType
        val listType = simpleTypes.singleOrNull { it.javaSimpleName == type.elementType.javaType } ?: return false
        if (elementType is TBlob<*>) {
          if (elementType.hasSoftLinks(simpleTypes)) {
            return true
          }
        }
        listType.hasSoftLinks(simpleTypes)
      }
    }
    is TOptional<*> -> {
      val optionalType = type.type
      if (optionalType is TBlob<*> && optionalType.isPersistentId(simpleTypes)) {
        true
      }
      else {
        val elementType = type.type
        val listType = simpleTypes.singleOrNull { it.javaSimpleName == type.type.javaType } ?: return false
        if (elementType is TBlob<*>) {
          if (elementType.hasSoftLinks(simpleTypes)) {
            return true
          }
        }
        listType.hasSoftLinks(simpleTypes)
      }
    }
    else -> return false
  }
}

internal fun TBlob<*>.hasSoftLinks(simpleTypes: List<DefType>): Boolean {
  if (this.isPersistentId(simpleTypes)) {
    return true
  }
  if (this.isDataClass(simpleTypes)) {
    val dataClass = this.getDataClass(simpleTypes)
    return dataClass.hasSoftLinks(simpleTypes)
  }
  if (this.isSealedClass(simpleTypes)) {
    val children = collectSealedChildren(simpleTypes.single { it.name == this.javaSimpleName }, simpleTypes)
    return children.any {
      it.hasSoftLinks(simpleTypes)
    }
  }
  return false
}

private fun collectSealedChildren(defType: DefType, simpleTypes: List<DefType>): List<DefType> {
  val directChildren = simpleTypes.filter { it.javaSuperType == defType.javaSimpleName }
  val otherChildren = directChildren.flatMap { collectSealedChildren(it, simpleTypes) }
  return directChildren + otherChildren
}

internal fun TBlob<*>.isPersistentId(simpleTypes: List<DefType>): Boolean {
  val thisType = simpleTypes.find { it.name == javaSimpleName } ?: return false
  return thisType.ispersistentId()
}

private fun DefType.ispersistentId(): Boolean {
  return def
    .superTypes
    .any { "PersistentEntityId" in it.classifier }
}

internal fun TBlob<*>.isDataClass(simpleTypes: List<DefType>): Boolean {
  return simpleTypes.find { it.name == this.javaSimpleName }?.def?.kind == WsData
}

internal fun TBlob<*>.isSealedClass(simpleTypes: List<DefType>): Boolean {
  val clazz = simpleTypes.find { it.name == this.javaSimpleName }
  return clazz.isSealedClass()
}

private fun DefType?.isSealedClass() = this?.def?.kind == WsSealed

internal fun TBlob<*>.getDataClass(simpleTypes: List<DefType>): DefType {
  return simpleTypes.find { it.name == this.javaSimpleName }!!
}

private fun ObjType<*, *>.operate(
  context: LinesBuilder,
  simpleTypes: List<DefType>,
  operation: LinesBuilder.(String) -> Unit
) {
  structure.allFields.noPersistentId().noRefs().forEach { field ->
    field.type.operate(field.name, simpleTypes, context, operation)
  }
}

private fun ValueType<*>.operate(
  varName: String,
  simpleTypes: List<DefType>,
  context: LinesBuilder,
  operation: LinesBuilder.(String) -> Unit,
  generateNewName: Boolean = true,
) {
  when (this) {
    is TBlob<*> -> {
      if (isPersistentId(simpleTypes)) {
        context.operation(varName)
      } else if (isDataClass(simpleTypes)) {
        val dataClass = getDataClass(simpleTypes)
        dataClass.structure.declaredFields.filter { it.constructorField }.forEach {
          it.type.operate("$varName.${it.name}", simpleTypes, context, operation)
        }
      } else if (isSealedClass(simpleTypes)) {
        val thisClass = simpleTypes.single { it.name == this.javaSimpleName }
        processSealedClass(simpleTypes, thisClass, varName, context, operation, generateNewName)
      }
    }
    is TCollection<*, *> -> {
      val elementType = elementType

      context.section("for (item in ${varName})") {
        elementType.operate("item", simpleTypes, this@section, operation, false)
      }
    }
    is TOptional<*> -> {
      if (type is TBlob && type.isPersistentId(simpleTypes)) {
        context.line("val optionalLink_${varName.clean()} = $varName")
        context.`if`("optionalLink_${varName.clean()} != null") label@{
          type.operate("optionalLink_${varName.clean()}", simpleTypes, this@label, operation)
        }
      }
    }
    else -> Unit
  }
}

private fun processSealedClass(simpleTypes: List<DefType>,
                               thisClass: DefType,
                               varName: String,
                               context: LinesBuilder,
                               operation: LinesBuilder.(String) -> Unit,
                               generateNewName: Boolean = true) {
  val children = simpleTypes.filter { it.javaSuperType == thisClass.javaSimpleName }
  val newVarName = if (generateNewName) "_${varName.clean()}" else varName
  if (generateNewName) context.line("val $newVarName = $varName")
  context.section("when ($newVarName)") {
    listBuilder(children) { item ->
      val linesBuilder = LinesBuilder(StringBuilder(), "${context.indent}    ").wrapper()
      if (item.isSealedClass()) {
        processSealedClass(simpleTypes, item, newVarName, linesBuilder, operation, generateNewName)
      }
      item.structure.declaredFields.filter { it.constructorField }.forEach {
        it.type.operate("$newVarName.${it.name}", simpleTypes, linesBuilder, operation)
      }
      section("is ${item.javaFullName} -> ") {
        result.append(linesBuilder.result)
      }
    }
  }
}


private fun ObjType<*, *>.operateUpdateLink(context: LinesBuilder, simpleTypes: List<DefType>) {
  structure.allFields.noPersistentId().noRefs().forEach { field ->
    val type = field.type
    val retType = type.processType(simpleTypes, context, field.name)
    if (retType != null) {
      context.`if`("$retType != null") {
        line("${field.name} = $retType")
      }
    }
  }
}

private fun ValueType<*>.processType(
  simpleTypes: List<DefType>,
  context: LinesBuilder,
  varName: String,
): String? {
  return when (this) {
    is TBlob<*> -> {
      if (isPersistentId(simpleTypes)) {
        val name = "${varName.clean()}_data"
        context.lineNoNl("val $name = ")
        context.ifElse("$varName == oldLink", {
          line("changed = true")
          line("newLink as $javaSimpleName")
        }) { line("null") }
        return name
      } else if (isDataClass(simpleTypes)) {

        val dataClass = getDataClass(simpleTypes)
        val updates = dataClass.structure.declaredFields.filter { it.constructorField }.mapNotNull label@{
          val retVar = it.type.processType(
            simpleTypes,
            context,
            "$varName.${it.name}"
          )
          if (retVar != null) it.name to retVar else null
        }
        if (updates.isEmpty()) {
          return null
        }
        else {
          val name = "${varName.clean()}_data"
          context.line("var $name = $varName")
          updates.forEach { (fieldName, update) ->
            context.`if`("$update != null") {
              line("$name = $name.copy($fieldName = $update)")
            }
          }
          return name
        }
      } else if (isSealedClass(simpleTypes)) {
        val thisClass = simpleTypes.single { it.name == this.javaSimpleName }
        return processSealedClass(simpleTypes, thisClass, varName, context)
      }
      return null
    }
    is TCollection<*, *> -> {
      var name: String? = "${varName.clean()}_data"
      val builder = lines(indent = context.indent) {
        section("val $name = $varName.map") label@{
          val returnVar = elementType.processType(
            simpleTypes,
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
      return name
    }
    is TOptional<*> -> {
      var name: String? = "${varName.clean()}_data_optional"
      val builder = lines(indent = context.indent) {
        lineNoNl("var $name = ")
        ifElse("$varName != null", labelIf@{
          val returnVar = type.processType(
            simpleTypes,
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
      return name
    }
    else -> return null
  }
}

private fun processSealedClass(simpleTypes: List<DefType>,
                               thisClass: DefType,
                               varName: String,
                               context: LinesBuilder): String {
  val children = simpleTypes.filter { it.javaSuperType == thisClass.javaSimpleName }
  val newVarName = "_${varName.clean()}"
  val resVarName = "res_${varName.clean()}"
  context.line("val $newVarName = $varName")
  context.lineNoNl("val $resVarName = ")
  context.section("when ($newVarName)") {
    listBuilder(children) { item ->
      section("is ${item.javaFullName} -> ") label@{
        var sectionVarName = newVarName
        if (item.isSealedClass()) {
          sectionVarName = processSealedClass(simpleTypes, item, sectionVarName, this)
        }
        val updates = item.structure.declaredFields.filter { it.constructorField }.mapNotNull {
          val retVar = it.type.processType(
            simpleTypes,
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