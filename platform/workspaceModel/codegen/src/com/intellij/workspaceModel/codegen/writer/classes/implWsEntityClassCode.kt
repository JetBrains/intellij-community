package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.codegen.indentRestOnly
import com.intellij.workspaceModel.codegen.javaFullName
import com.intellij.workspaceModel.codegen.javaImplName
import com.intellij.workspaceModel.codegen.lines
import com.intellij.workspaceModel.codegen.allRefsFields
import com.intellij.workspaceModel.codegen.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.fields.refsConnectionIdCode
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.deft.ObjType

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


