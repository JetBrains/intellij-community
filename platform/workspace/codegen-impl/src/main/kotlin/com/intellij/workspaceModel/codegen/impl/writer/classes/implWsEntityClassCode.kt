package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionIdCode
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*

fun ObjClass<*>.implWsEntityCode(): String {
  return """
package ${module.name}    

@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})
@${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})
$generatedCodeVisibilityModifier ${if (openness.instantiatable) "open" else "abstract"} class $javaImplName(private val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase}(dataSource) {
    ${
    """
    private companion object {
        ${allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
        
${getLinksOfConnectionIds(this)}
    }"""
  }
        
    ${allFields.filter { it.name !in listOf("entitySource", "symbolicId") }.lines("    ") { implWsEntityFieldCode }.trimEnd()}

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }
    
    override fun connectionIdList(): List<${ConnectionId}> {
        return connections
    }
  

    ${implWsEntityBuilderCode().indentRestOnly("    ")}
}
    """.trimIndent()
}

private fun getLinksOfConnectionIds(type: ObjClass<*>): String {
  return lines(2) {
    line("private val connections = listOf<$ConnectionId>(")
    type.allRefsFields.forEach {
      line("    " + it.refsConnectionId + ",")
    }
    line(")")
  }
}
