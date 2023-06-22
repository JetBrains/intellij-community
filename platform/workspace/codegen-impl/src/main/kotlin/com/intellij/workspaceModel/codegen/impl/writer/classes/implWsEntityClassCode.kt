package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionIdCode
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator

fun ObjClass<*>.implWsEntityCode(): String {
  return """
package ${module.name}    

@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersionCalculator.apiVersion})
@${GeneratedCodeImplVersion::class.fqn}(${CodeGeneratorVersionCalculator.implementationMajorVersion})
${if (openness.instantiatable) "open" else "abstract"} class $javaImplName(val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase::class.fqn}() {
    ${
    """
    companion object {
        ${allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
        
${getLinksOfConnectionIds(this)}
    }"""
  }
        
    ${allFields.filter { it.name !in listOf("entitySource", "symbolicId") }.lines("    ") { implWsEntityFieldCode }.trimEnd()}

    override val entitySource: EntitySource
        get() = dataSource.entitySource
    
    override fun connectionIdList(): List<${ConnectionId::class.fqn}> {
        return connections
    }

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
}
    """.trimIndent()
}

private fun getLinksOfConnectionIds(type: ObjClass<*>): String {
  return lines(2) {
    line("val connections = listOf<${ConnectionId::class.fqn}>(")
    type.allRefsFields.forEach {
      line("    " + it.refsConnectionId + ",")
    }
    line(")")
  }
}
