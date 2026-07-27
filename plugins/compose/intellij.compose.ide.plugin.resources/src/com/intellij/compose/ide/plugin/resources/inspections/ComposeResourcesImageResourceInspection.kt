// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compose.ide.plugin.resources.ResourceItem
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.getResourceItem
import com.intellij.compose.ide.plugin.resources.intentions.ComposeResourcesImageResourceQuickFix
import com.intellij.compose.ide.plugin.shared.COMPOSE_FOUNDATION_IMAGE_CALLABLE_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_CALLABLE_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_NAME
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.isCallTo
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ComposeResourcesImageResourceInspection : AbstractKotlinInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    if (session.file !is KtFile) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        if (!expression.hasImageResourceCalleeName()) return
        if (!expression.isCallTo(COMPOSE_IMAGE_RESOURCE_CALLABLE_ID)) return
        if (!expression.hasVectorResourceArgument()) return

        val calleeExpression = expression.calleeExpression ?: return
        val fixes: Array<LocalQuickFix> =
          if (expression.isDirectComposeImageArgument()) arrayOf(ComposeResourcesImageResourceQuickFix()) else LocalQuickFix.EMPTY_ARRAY
        val message = ComposeIdeBundle.message("compose.inspection.image.resource.description")

        val qualifiedExpression = expression.getQualifiedExpressionForSelector()
        if (qualifiedExpression != null) {
          val range = TextRange(0, calleeExpression.textRangeIn(qualifiedExpression).endOffset)
          holder.registerProblem(qualifiedExpression, range, message, *fixes)
        }
        else {
          holder.registerProblem(calleeExpression, message, *fixes)
        }
      }
    }
  }

  private fun KtCallExpression.hasImageResourceCalleeName(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == COMPOSE_IMAGE_RESOURCE_NAME.asString()

  private fun KtCallExpression.hasVectorResourceArgument(): Boolean {
    val resourceItem = getDrawableResourceArgument() ?: return false
    return resourceItem.paths.any { it.isVectorResourcePath() }
  }

  private fun KtCallExpression.getDrawableResourceArgument(): ResourceItem? {
    val argumentExpression = valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
    val selectorExpression = (argumentExpression as? KtDotQualifiedExpression)?.selectorExpression
    return sequenceOf(argumentExpression, selectorExpression)
      .filterNotNull()
      .mapNotNull { getResourceItem(it) }
      .firstOrNull { it.type == ResourceType.DRAWABLE }
  }

  private fun String.isVectorResourcePath(): Boolean =
    endsWith(".xml", ignoreCase = true) || endsWith(".svg", ignoreCase = true)

  private fun KtCallExpression.isDirectComposeImageArgument(): Boolean {
    val directParent = parent
    val valueArgument =
      when (directParent) {
        is KtValueArgument -> directParent
        is KtDotQualifiedExpression -> directParent.parent as? KtValueArgument
        else -> null
      } ?: return false
    val argumentList = valueArgument.parent as? KtValueArgumentList ?: return false
    val callExpression = argumentList.parent as? KtCallExpression ?: return false
    return callExpression.isCallTo(COMPOSE_FOUNDATION_IMAGE_CALLABLE_ID)
  }
}
