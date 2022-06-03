package com.intellij.workspaceModel.codegen.classes

import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.impl.ConnectionId
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
import com.intellij.workspaceModel.codegen.deft.TStructure
import com.intellij.workspaceModel.codegen.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.utils.lines
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder

fun ObjType<*, *>.implWsEntityCode(): String {
  return """
@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})
@${GeneratedCodeImplVersion::class.fqn}(${CodeGeneratorVersions.IMPL_VERSION})
${if (abstract) "abstract" else "open"} class $javaImplName: $javaFullName, ${WorkspaceEntityBase::class.fqn}() {
    ${
    """
    companion object {
        ${structure.allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
        
${getLinksOfConnectionIds(structure)}
    }"""
  }
        
    ${structure.allFields.filter { it.name !in listOf("entitySource", "persistentId") }.lines("    ") { implWsEntityFieldCode }.trimEnd()}
    
    override fun connectionIdList(): List<${ConnectionId::class.fqn}> {
        return connections
    }

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
}
    """.trimIndent()
}

private fun getLinksOfConnectionIds(structure: TStructure<out Obj, out ObjBuilder<*>>): String {
  return lines("        ") {
    line("val connections = listOf<${ConnectionId::class.fqn}>(")
    structure.allRefsFields.forEach {
      line("    " + it.refsConnectionId + ",")
    }
    line(")")
  }
}
