// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class IncorrectParentDisposableInspection : DevKitUastInspectionBase(UCallExpression::class.java) {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (PsiUtil.isIdeaProject(holder.file.project) && isPlatformFile(holder.file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        checkCallExpression(node, holder)

        return true
      }
    }, arrayOf(UCallExpression::class.java))
  }

  private fun isPlatformFile(file: PsiFile): Boolean {
    return "/platform/" in file.virtualFile.path  // TODO expand this check
  }

  private val sdkLink = "(<a href=\"https://www.jetbrains.org/intellij/sdk/docs/basics/disposers.html#choosing-a-disposable-parent\">Choosing a Disposable Parent</a>)"

  private fun checkCallExpression(node: UCallExpression, holder: ProblemsHolder) {
    val psiMethod = node.resolve() ?: return
    psiMethod.parameters.forEachIndexed { index, parameter ->
      val parameterType = (parameter.type as? PsiClassType)?.resolve() ?: return@forEachIndexed
      if (parameterType.qualifiedName != Disposable::class.java.name) return@forEachIndexed
      val argumentForParameter = node.getArgumentForParameter(index) ?: return@forEachIndexed
      val argumentSourcePsi = argumentForParameter.sourcePsi ?: return@forEachIndexed
      val argumentType = (argumentForParameter.getExpressionType() as? PsiClassType)?.resolve() ?: return@forEachIndexed
      if (argumentType.qualifiedName == Project::class.java.name) {
        holder.registerProblem(argumentSourcePsi, "<html>Don't use Project as disposable in plugin code $sdkLink</html>")
      }
      else if (argumentType.qualifiedName == Application::class.java.name) {
        holder.registerProblem(argumentSourcePsi, "<html>Don't use Application as disposable in plugin code $sdkLink</html>")
      }
    }
  }
}
