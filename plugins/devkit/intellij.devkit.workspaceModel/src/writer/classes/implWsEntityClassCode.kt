package org.jetbrains.deft.codegen.ijws.classes

import deft.storage.codegen.indentRestOnly
import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaImplName
import deft.storage.codegen.lines
import org.jetbrains.deft.codegen.ijws.allRefsFields
import org.jetbrains.deft.codegen.ijws.fields.implWsEntityFieldCode
import org.jetbrains.deft.codegen.ijws.fields.refsConnectionIdCode
import org.jetbrains.deft.codegen.ijws.wsFqn
import org.jetbrains.deft.impl.ObjType

fun ObjType<*, *>.implWsEntityCode(): String {
    return """

${ if (abstract) "abstract" else "open" } class $javaImplName: $javaFullName, ${wsFqn("WorkspaceEntityBase")}() {
    ${if (structure.allRefsFields.isNotEmpty()) """
    companion object {
        ${structure.allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
    }""" else ""}
        
    ${structure.allFields.filter { it.name !in listOf("entitySource", "persistentId") }.lines("    ") { implWsEntityFieldCode }.trimEnd()}

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
    
    // TODO: Fill with the data from the current entity
    fun builder(): ObjBuilder<*> = Builder($javaDataName())
}
    """.trimIndent()
}


