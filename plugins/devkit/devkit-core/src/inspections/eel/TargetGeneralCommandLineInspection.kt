// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TargetGeneralCommandLineInspection : DevKitUastInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return createFilteredUastVisitor(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
            if (node.classReference?.sourcePsi?.text?.substringAfterLast('.') != GENERAL_COMMAND_LINE_NAME) return true
          }
          else if (node.methodName != WITH_EXE_PATH_METHOD) {
            return true
          }

          val method = node.resolve() ?: return true
          if (method.containingClass?.qualifiedName != GENERAL_COMMAND_LINE_CLASS) return true

          if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
            if (!reportTargetExecutable(node, holder)) reportMigrationOpportunity(node, holder)
          }
          else {
            reportTargetExecutable(node, holder)
          }
          return true
        }
      },
      arrayOf(UCallExpression::class.java),
    ) { element ->
      val text = element.text
      GENERAL_COMMAND_LINE_NAME in text || WITH_EXE_PATH_METHOD in text
    }
  }

  private fun reportTargetExecutable(node: UCallExpression, holder: ProblemsHolder): Boolean {
    val executable = node.valueArguments.firstOrNull() ?: return false
    if (usesEnvironmentAwareNioPath(executable)) return false
    val environment = EelEnvironmentClassifier.classify(executable)
    if (environment != EelEnvironment.TARGET && environment != EelEnvironment.MIXED) return false

    holder.registerProblem(
      executable.sourcePsi ?: return false,
      DevKitBundle.message("inspection.target.general.command.line.lost.routing"),
    )
    return true
  }

  private fun usesEnvironmentAwareNioPath(expression: UExpression): Boolean = when (expression) {
    is UCallExpression -> expression.methodName == "asNioPath" || expression.receiver?.let(::usesEnvironmentAwareNioPath) == true
    is UQualifiedReferenceExpression -> usesEnvironmentAwareNioPath(expression.receiver) || usesEnvironmentAwareNioPath(expression.selector)
    else -> false
  }

  private fun reportMigrationOpportunity(node: UCallExpression, holder: ProblemsHolder) {
    val method = node.getContainingUMethod() ?: return
    val hasTargetContext = method.uastParameters.any {
      EelEnvironmentClassifier.classify(it.type) == EelEnvironment.TARGET
    }
    if (!hasTargetContext) return

    holder.registerProblem(
      node.methodIdentifier?.sourcePsi ?: return,
      DevKitBundle.message("inspection.target.general.command.line.migration"),
      ProblemHighlightType.WEAK_WARNING,
    )
  }

  private companion object {
    const val GENERAL_COMMAND_LINE_NAME = "GeneralCommandLine"
    const val GENERAL_COMMAND_LINE_CLASS = "com.intellij.execution.configurations.GeneralCommandLine"
    const val WITH_EXE_PATH_METHOD = "withExePath"
  }
}
