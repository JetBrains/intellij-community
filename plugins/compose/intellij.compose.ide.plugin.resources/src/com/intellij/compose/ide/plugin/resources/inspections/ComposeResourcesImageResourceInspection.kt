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
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_BITMAP_PARAMETER_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_FILTER_QUALITY_PARAMETER_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_CALLABLE_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_NAME
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.isCallTo
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

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
        val fixes: Array<LocalQuickFix> = expression.createImageResourceQuickFix()?.let { arrayOf(it) } ?: LocalQuickFix.EMPTY_ARRAY
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

  /** Returns the quick fix when this call is the `bitmap` argument of the Compose `Image` call */
  @OptIn(KaExperimentalApi::class)
  private fun KtCallExpression.createImageResourceQuickFix(): ComposeResourcesImageResourceQuickFix? {
    // The `imageResource` call must be a direct argument, optionally with a qualifier
    val bitmapArgument = getQualifiedExpressionForSelectorOrThis().parent as? KtValueArgument ?: return null
    val imageCall = bitmapArgument.parentOfType<KtCallExpression>() ?: return null

    return analyze(imageCall) {
      // A custom function with the name `Image` gets no quick fix
      val call = imageCall.tryResolveCall()?.single?.function ?: return@analyze null
      if (call.symbol.callableId != COMPOSE_FOUNDATION_IMAGE_CALLABLE_ID) return@analyze null

      // Match the parameter, because the argument can be positional
      val argumentMapping = call.valueArgumentMapping
      if (argumentMapping[bitmapArgument.getArgumentExpression()]?.name != COMPOSE_IMAGE_BITMAP_PARAMETER_NAME) return@analyze null

      // Find the `filterQuality` argument for the quick fix to remove
      val filterQualityExpression = argumentMapping.entries.find { it.value.name == COMPOSE_IMAGE_FILTER_QUALITY_PARAMETER_NAME }?.key
      ComposeResourcesImageResourceQuickFix(filterQualityExpression?.parent as? KtValueArgument)
    }
  }
}
