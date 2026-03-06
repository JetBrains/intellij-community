// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that reports calls to @Composable functions from modules
 * that don't have the Compose compiler plugin configured.
 */
@ApiStatus.Internal
abstract class ComposeMissingPluginInspection : AbstractKotlinInspection() {

  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR

  override fun getShortName(): String = "ComposableCallWithoutComposePlugin"

  abstract fun isComposePluginApplied(module: Module): Boolean

  abstract fun createQuickFix(): LocalQuickFix

  abstract fun isComposableInvocation(expression: KtCallExpression): Boolean

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val module = ModuleUtilCore.findModuleForFile(session.file) ?: return PsiElementVisitor.EMPTY_VISITOR

    if (isComposePluginApplied(module)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (isComposableInvocation(expression)) {
          holder.registerProblem(
            expression.calleeExpression ?: expression,
            ComposeIdeBundle.message("compose.inspection.missing.plugin.name"),
            ProblemHighlightType.ERROR,
            createQuickFix(),
          )
        }
      }
    }
  }
}