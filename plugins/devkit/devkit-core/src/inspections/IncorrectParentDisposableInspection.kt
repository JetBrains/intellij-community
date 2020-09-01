// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class IncorrectParentDisposableInspection : DevKitUastInspectionBase(UCallExpression::class.java) {

  override fun isAllowed(holder: ProblemsHolder): Boolean =
    DevKitInspectionBase.isAllowedInPluginsOnly(holder)

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        checkCallExpression(node, holder)

        return true
      }
    }, arrayOf(UCallExpression::class.java))

  private val sdkLink = "https://www.jetbrains.org/intellij/sdk/docs/basics/disposers.html#choosing-a-disposable-parent"

  private fun checkCallExpression(node: UCallExpression, holder: ProblemsHolder) {
    val psiMethod = node.resolve() ?: return
    psiMethod.parameters.forEachIndexed { index, parameter ->
      val parameterType = (parameter.type as? PsiClassType)?.resolve() ?: return@forEachIndexed
      if (parameterType.qualifiedName != Disposable::class.java.name) return@forEachIndexed
      val argumentForParameter = node.getArgumentForParameter(index) ?: return@forEachIndexed
      val argumentSourcePsi = argumentForParameter.sourcePsi ?: return@forEachIndexed
      val argumentType = (argumentForParameter.getExpressionType() as? PsiClassType)?.resolve() ?: return@forEachIndexed

      @NlsSafe var typeName: String? = null
      when (argumentType.qualifiedName) {
        Project::class.java.name -> typeName = "Project"
        Application::class.java.name -> typeName = "Application"
        Module::class.java.name -> typeName = "Module"
      }
      
      if (typeName != null) {
        holder.registerProblem(argumentSourcePsi, HtmlBuilder()
          .append(DevKitBundle.message("inspections.IncorrectParentDisposableInspection.do.not.use.as.disposable", typeName))
          .nbsp()
          .append("(")
          .appendLink(sdkLink, DevKitBundle.message("inspections.IncorrectParentDisposableInspection.documentation.link.title"))
          .append(")")
          .wrapWith(HtmlChunk.html())
          .toString())
      }
    }
  }
}
