package org.jetbrains.deft.codegen.ijws.classes

import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import deft.storage.codegen.indentRestOnly
import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaImplName
import deft.storage.codegen.lines
import org.jetbrains.deft.codegen.ijws.allRefsFields
import org.jetbrains.deft.codegen.ijws.fields.implWsEntityFieldCode
import org.jetbrains.deft.codegen.ijws.fields.refsConnectionIdCode
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.impl.ObjType

fun ObjType<*, *>.implWsEntityCode(): String {
  return """
@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})
@${GeneratedCodeImplVersion::class.fqn}(${CodeGeneratorVersions.IMPL_VERSION})
${if (abstract) "abstract" else "open"} class $javaImplName: $javaFullName, ${WorkspaceEntityBase::class.fqn}() {
    ${
    if (structure.allRefsFields.isNotEmpty()) """
    companion object {
        ${structure.allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
    }"""
    else ""
  }
        
    ${structure.allFields.filter { it.name !in listOf("entitySource", "persistentId") }.lines("    ") { implWsEntityFieldCode }.trimEnd()}

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
}
    """.trimIndent()
}


