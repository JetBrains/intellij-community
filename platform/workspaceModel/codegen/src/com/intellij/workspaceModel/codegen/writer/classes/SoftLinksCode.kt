package org.jetbrains.deft.codegen.ijws.classes

import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaSuperType
import org.jetbrains.deft.codegen.ijws.model.WsData
import org.jetbrains.deft.codegen.ijws.model.WsSealed
import org.jetbrains.deft.codegen.ijws.wsFqn
import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.codegen.utils.LinesBuilder
import org.jetbrains.deft.codegen.utils.lines
import org.jetbrains.deft.impl.*

internal fun ObjType<*, *>.softLinksCode(context: LinesBuilder, hasSoftLinks: Boolean, simpleTypes: List<DefType>) {
    context.conditionalLine({ hasSoftLinks }, "override fun getLinks(): Set<${wsFqn("PersistentEntityId")}<*>>") {
        line("val result = HashSet<PersistentEntityId<*>>()")
        operate(this@conditionalLine, simpleTypes) { line("result.add($it)") }
        line("return result")
    }

    context.conditionalLine(
        { hasSoftLinks },
        "override fun index(index: ${wsFqn("WorkspaceMutableIndex")}<PersistentEntityId<*>>)"
    ) {
        operate(this@conditionalLine, simpleTypes) { line("index.index(this, $it)") }
    }

    context.conditionalLine(
        { hasSoftLinks },
        "override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: ${wsFqn("WorkspaceMutableIndex")}<PersistentEntityId<*>>)"
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
        "override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean"
    ) {
        line("var changed = false")
        operateUpdateLink(this@conditionalLine, simpleTypes)
        line("return changed")
    }
}

internal fun ObjType<*, *>.hasSoftLinks(simpleTypes: List<DefType>): Boolean {
    return structure.allFields.noPersistentId().any { field ->
        val type = field.type
        if (field.name == "persistentId") return@any false
        when (type) {
            is TBlob<*> -> type.isPersistentId(simpleTypes)
            else -> false
        }
    }
}

internal fun TBlob<*>.isPersistentId(simpleTypes: List<DefType>): Boolean {
    val thisType = simpleTypes.find { it.name == javaSimpleName } ?: return false
    return thisType
        .def
        .superTypes
        .any { "PersistentEntityId" in it.classifier }
}

internal fun TBlob<*>.isDataClass(simpleTypes: List<DefType>): Boolean {
    return simpleTypes.find { it.name == this.javaSimpleName }?.def?.kind == WsData
}

internal fun TBlob<*>.isSealedClass(simpleTypes: List<DefType>): Boolean {
    return simpleTypes.find { it.name == this.javaSimpleName }?.def?.kind == WsSealed
}

internal fun TBlob<*>.getDataClass(simpleTypes: List<DefType>): DefType {
    return simpleTypes.find { it.name == this.javaSimpleName }!!
}

private fun ObjType<*, *>.operate(
    context: LinesBuilder,
    simpleTypes: List<DefType>,
    operation: LinesBuilder.(String) -> Unit
) {
    structure.allFields.noPersistentId().forEach { field ->
        field.type.operate(field.name, simpleTypes, context, operation)
    }
}

private fun ValueType<*>.operate(
    varName: String,
    simpleTypes: List<DefType>,
    context: LinesBuilder,
    operation: LinesBuilder.(String) -> Unit
) {
    when (this) {
        is TBlob<*> -> {
            if (isPersistentId(simpleTypes)) {
                context.operation(varName)
            }
            if (isDataClass(simpleTypes)) {
                val dataClass = getDataClass(simpleTypes)
                dataClass.structure.declaredFields.filter { it.constructorField }.forEach {
                    it.type.operate("$varName.${it.name}", simpleTypes, context, operation)
                }
            }
            if (isSealedClass(simpleTypes)) {
                val children = simpleTypes.filter { it.javaSuperType == this.javaSimpleName }
                val newVarName = "_$varName"
                context.line("val $newVarName = $varName")
                context.section("when ($newVarName)") {
                    listBuilder(children) { item ->
                        section("is ${item.javaFullName} -> ") label@ {
                            item.structure.declaredFields.filter { it.constructorField }.forEach {
                                it.type.operate("$newVarName.${it.name}", simpleTypes, this@label, operation)
                            }
                        }
                    }
                }
            }
        }
        is TList<*> -> {
            val elementType = elementType

            context.section("for (item in ${varName})") {
                elementType.operate("item", simpleTypes, this@section, operation)
            }
        }
        is TOptional<*> -> {
            val type = type
            context.line("val optionalLink_${varName.clean()} = $varName")
            context.`if`("optionalLink_${varName.clean()} != null") label@{
                type.operate("optionalLink_${varName.clean()}", simpleTypes, this@label, operation)
            }
        }
        else -> Unit
    }
}

private fun ObjType<*, *>.operateUpdateLink(context: LinesBuilder, simpleTypes: List<DefType>) {
    structure.allFields.noPersistentId().forEach { field ->
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
            }
            if (isDataClass(simpleTypes)) {

                val dataClass = getDataClass(simpleTypes)
                val updates = dataClass.structure.declaredFields.filter { it.constructorField }.mapNotNull label@ {
                    val retVar = it.type.processType(
                        simpleTypes,
                        context,
                        "$varName.${it.name}"
                    )
                    if (retVar != null) it.name to retVar else null
                }
                if (updates.isEmpty()) {
                    return null
                } else {
                    val name = "${varName.clean()}_data"
                    context.line("var $name = $varName")
                    updates.forEach { (fieldName, update) ->
                        context.`if`("$update != null") {
                            line("$name = $name.copy($fieldName = $update)")
                        }
                    }
                    return name
                }
            }
            if (isSealedClass(simpleTypes)) {
                val children = simpleTypes.filter { it.javaSuperType == this.javaSimpleName }
                val newVarName = "_${varName.clean()}"
                val resVarName = "res_${varName.clean()}"
                context.line("val $newVarName = $varName")
                context.lineNoNl("val $resVarName = ")
                context.section("when ($newVarName)") {
                    listBuilder(children) { item ->
                        section("is ${item.javaFullName} -> ") label@ {
                            val updates = item.structure.declaredFields.filter { it.constructorField }.mapNotNull {
                                val retVar = it.type.processType(
                                    simpleTypes,
                                    this@label,
                                    "$newVarName.${it.name}"
                                )
                                if (retVar != null) it.name to retVar else null
                            }
                            if (updates.isEmpty()) {
                                line(newVarName)
                            } else {
                                val name = "${newVarName.clean()}_data"
                                line("var $name = $newVarName")
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
            return null
        }
        is TList<*> -> {
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
                ifElse("$varName != null", labelIf@ {
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
                }) { line("null")}
            }
            if (name != null) {
                context.result.append(builder)
            }
            return name
        }
        else -> return null
    }
}

private fun String.clean(): String {
    return this.replace(".", "_").replace('!', '_')
}