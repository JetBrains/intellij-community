// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.writer.ConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.EntityStorageInstrumentationApi
import com.intellij.workspaceModel.codegen.impl.writer.GeneratedCodeApiVersion
import com.intellij.workspaceModel.codegen.impl.writer.GeneratedCodeImplVersion
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityBase
import com.intellij.workspaceModel.codegen.impl.writer.WorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.writer.extensions.additionalAnnotations
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allRefsFields
import com.intellij.workspaceModel.codegen.impl.writer.extensions.defaultJavaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaFullName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName
import com.intellij.workspaceModel.codegen.impl.writer.fields.implWsEntityFieldCode
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionId
import com.intellij.workspaceModel.codegen.impl.writer.fields.refsConnectionIdCode
import com.intellij.workspaceModel.codegen.impl.writer.lines
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdFieldName
import com.intellij.workspaceModel.codegen.impl.writer.symbolicIdImplCode

fun ObjClass<*>.implWsEntityCode(): String {
  val inheritanceModifier = when {
    openness.extendable && !openness.instantiatable -> "abstract "
    openness.extendable && openness.instantiatable -> "open "
    else -> ""
  }

  return """@file:OptIn($EntityStorageInstrumentationApi::class)

package ${module.implPackage}   
 
import $module.${defaultJavaBuilderName}

$implWsEntityAnnotations
@OptIn($WorkspaceEntityInternalApi::class)
internal ${inheritanceModifier}class $javaImplName(private val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase}(dataSource) {

private companion object {
${allRefsFields.lines { refsConnectionIdCode }.trimEnd()}
${getLinksOfConnectionIds(this)}
}
${symbolicIdImplCode()}
${allFields.filter { it.name !in listOf("entitySource", symbolicIdFieldName) }.lines { implWsEntityFieldCode }.trimEnd()}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<${ConnectionId}> {
return connections
}

${implWsEntityBuilderCode()}
}
"""
}

private val ObjClass<*>.implWsEntityAnnotations: String
  get() {
    return lines {
      if (additionalAnnotations.isNotEmpty()) {
        list(additionalAnnotations)
      }
      line("@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
      lineNoNl("@${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})")
    }
  }

private fun getLinksOfConnectionIds(type: ObjClass<*>): String {
  return lines {
    line(type.allRefsFields.joinToString(separator = ",",
                                         prefix = "private val connections = listOf<$ConnectionId>(",
                                         postfix = ")") { it.refsConnectionId })
  }
}
