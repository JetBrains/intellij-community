// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionIdCode
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.javaType

fun ObjClass<*>.implWsEntityCode(): String {
  val inheritanceModifier = when {
    openness.extendable && !openness.instantiatable -> "abstract"
    openness.extendable && openness.instantiatable -> "open"
    else -> ""
  }

  return """
package ${module.implPackage}   
 
import $module.${defaultJavaBuilderName}

${implWsEntityAnnotations}
@OptIn($WorkspaceEntityInternalApi::class)
internal $inheritanceModifier class $javaImplName(private val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase}(dataSource) {
    ${
    """
    private companion object {
        ${allRefsFields.lines("        ") { refsConnectionIdCode }.trimEnd()}
        
${getLinksOfConnectionIds(this)}
    }"""
  }
    ${allFields.find { it.name == "symbolicId" }?.let { "override val symbolicId: ${it.valueType.javaType} = super.symbolicId\n" } ?: ""}
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

private val ObjClass<*>.implWsEntityAnnotations: String
  get() {
    return lines {
      if (additionalAnnotations.isNotEmpty()) {
        line(additionalAnnotations)
      }
      line("@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
      lineNoNl("@${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})")
    }
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
