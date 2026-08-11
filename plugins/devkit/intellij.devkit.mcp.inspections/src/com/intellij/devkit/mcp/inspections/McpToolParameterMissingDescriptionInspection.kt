// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class McpToolParameterMissingDescriptionInspection : McpToolInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        if (!node.hasMcpToolAnnotation()) return true
        for (parameter in node.uastParameters) {
          val parameterName = (parameter.javaPsi as? PsiNamedElement)?.name ?: continue
          if (parameterName == PROJECT_PATH_PARAMETER_NAME || parameter.hasMcpDescriptionAnnotation()) continue
          if (parameter.nameElement() == null) continue
          holder.registerUProblem(
            parameter,
            DevKitMcpInspectionsBundle.message("inspection.mcp.tool.parameter.missing.description.message", parameterName),
          )
        }
        return true
      }
    }, arrayOf(UMethod::class.java))
}
