// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TargetSystemInfoInspection : DevKitUastInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return createFilteredUastVisitor(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
          val receiverText = node.receiver.sourcePsi?.text
          if (receiverText != "SystemInfo" && receiverText != SYSTEM_INFO_CLASS) return true

          val field = node.resolve() as? PsiField ?: return true
          if (field.containingClass?.qualifiedName != SYSTEM_INFO_CLASS || !field.name.startsWith("is")) return true

          val method = node.getContainingUMethod() ?: return true
          val context = method.uastParameters
            .map { EelEnvironmentClassifier.classify(it.type) }
            .fold(EelEnvironment.UNKNOWN, ::merge)
          if (context != EelEnvironment.TARGET && context != EelEnvironment.MIXED) return true

          holder.registerProblem(
            node.selector.sourcePsi ?: return true,
            DevKitBundle.message(
              if (method.uastParameters.any { it.type.canonicalText == PROJECT_CLASS }) {
                "inspection.target.system.info.project.os"
              }
              else {
                "inspection.target.system.info.environment.os"
              },
            ),
          )
          return true
        }
      },
      arrayOf(UQualifiedReferenceExpression::class.java),
    ) { element -> "SystemInfo" in element.text }
  }

  private fun merge(first: EelEnvironment, second: EelEnvironment): EelEnvironment {
    if (first == EelEnvironment.UNKNOWN) return second
    if (second == EelEnvironment.UNKNOWN) return first
    if (first == second) return first
    return EelEnvironment.MIXED
  }

  private companion object {
    const val PROJECT_CLASS = "com.intellij.openapi.project.Project"
    const val SYSTEM_INFO_CLASS = "com.intellij.openapi.util.SystemInfo"
  }
}
