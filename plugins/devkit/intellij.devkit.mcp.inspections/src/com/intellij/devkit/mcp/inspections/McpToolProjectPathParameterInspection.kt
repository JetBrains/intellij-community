// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiWhiteSpace
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class McpToolProjectPathParameterInspection : McpToolInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitMethod(node: UMethod): Boolean {
        if (!node.hasMcpToolAnnotation()) return true
        for (parameter in node.uastParameters) {
          if ((parameter.javaPsi as? PsiNamedElement)?.name != PROJECT_PATH_PARAMETER_NAME) continue
          if (parameter.nameElement() == null) continue
          holder.registerUProblem(
            parameter,
            DevKitMcpInspectionsBundle.message("inspection.mcp.tool.project.path.parameter.message"),
            RemoveProjectPathParameterFix(),
          )
        }
        return true
      }
    }, arrayOf(UMethod::class.java))
}

private class RemoveProjectPathParameterFix : ModCommandQuickFix(), DumbAware {
  override fun getFamilyName(): String =
    DevKitMcpInspectionsBundle.message("inspection.mcp.tool.project.path.parameter.fix.family.name")

  override fun getName(): @IntentionName String =
    DevKitMcpInspectionsBundle.message("inspection.mcp.tool.project.path.parameter.fix.name")

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val uParameter = descriptor.psiElement.getUastParentOfType<UParameter>()
    val sourceParameter = uParameter?.sourcePsi ?: uParameter?.javaPsi ?: descriptor.psiElement
    return ModCommand.psiUpdate(sourceParameter) { element ->
      element.deleteParameter()
    }
  }

  private fun PsiElement.deleteParameter() {
    if (this is PsiParameter) {
      delete()
    }
    else {
      deleteWithAdjacentComma()
    }
  }

  private fun PsiElement.deleteWithAdjacentComma() {
    val parent = parent ?: run {
      delete()
      return
    }
    val nextComma = findSiblingComma(forward = true)
    if (nextComma != null) {
      parent.deleteChildRange(this, nextComma)
      return
    }
    val previousComma = findSiblingComma(forward = false)
    if (previousComma != null) {
      parent.deleteChildRange(previousComma, this)
      return
    }
    delete()
  }

  private fun PsiElement.findSiblingComma(forward: Boolean): PsiElement? {
    var sibling = if (forward) nextSibling else prevSibling
    while (sibling != null) {
      if (sibling.text == ",") return sibling
      if (sibling !is PsiWhiteSpace && sibling !is PsiComment) return null
      sibling = if (forward) sibling.nextSibling else sibling.prevSibling
    }
    return null
  }
}
