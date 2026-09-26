// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class McpToolMissingDescriptionInspection : McpToolInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        if (node.hasMcpToolAnnotation() && !node.hasMcpDescriptionAnnotation() && node.nameElement() != null) {
          holder.registerUProblem(
            node,
            DevKitMcpInspectionsBundle.message("inspection.mcp.tool.missing.description.message", node.name),
          )
        }
        return true
      }
    }, arrayOf(UMethod::class.java))
}
