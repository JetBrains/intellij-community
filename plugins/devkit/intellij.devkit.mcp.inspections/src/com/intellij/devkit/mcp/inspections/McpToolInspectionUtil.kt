// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.sourcePsiElement

internal const val MCP_TOOL_FQN: String = "com.intellij.mcpserver.annotations.McpTool"
internal const val MCP_DESCRIPTION_FQN: String = "com.intellij.mcpserver.annotations.McpDescription"
internal const val PROJECT_PATH_PARAMETER_NAME: String = "projectPath"

internal abstract class McpToolInspectionBase : DevKitUastInspectionBase(UMethod::class.java) {
  override fun isAllowed(holder: ProblemsHolder): Boolean =
    super.isAllowed(holder) && DevKitInspectionUtil.isClassAvailable(holder, MCP_TOOL_FQN)

  protected fun UAnnotated.hasMcpToolAnnotation(): Boolean = findAnnotation(MCP_TOOL_FQN) != null

  protected fun UAnnotated.hasMcpDescriptionAnnotation(): Boolean = findAnnotation(MCP_DESCRIPTION_FQN) != null

  protected fun UMethod.nameElement(): PsiElement? = uastAnchor?.sourcePsi ?: sourcePsiElement

  protected fun UParameter.nameElement(): PsiElement? = uastAnchor?.sourcePsi ?: sourcePsiElement
}
