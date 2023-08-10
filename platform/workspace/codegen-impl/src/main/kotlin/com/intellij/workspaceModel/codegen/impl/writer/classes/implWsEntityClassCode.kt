package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionIdCode
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator

fun ObjClass<*>.implWsEntityCode(): String {
  return """
package ${module.name}    

@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})
@${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})
${if (openness.instantiatable) "open" else "abstract"} class $javaImplName(val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase}() {
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
    
    override fun connectionIdList(): List<${ConnectionId}> {
        return connections
    }

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
}
    """.trimIndent()
}

private fun getLinksOfConnectionIds(type: ObjClass<*>): String {
  return lines(2) {
    line("val connections = listOf<${ConnectionId}>(")
    type.allRefsFields.forEach {
      line("    " + it.refsConnectionId + ",")
    }
    line(")")
  }
}
