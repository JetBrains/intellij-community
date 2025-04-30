// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertUnsafeCastCallToUnsafeCastIntention : KotlinApplicableModCommandAction<KtDotQualifiedExpression, ConvertUnsafeCastCallToUnsafeCastIntention.Context>(
  KtDotQualifiedExpression::class
) {
  data class Context(
    val receiverText: String,
    val typeText: String,
  )

  override fun getFamilyName(): String = KotlinBundle.message("convert.to.unsafe.cast")

  override fun getPresentation(context: ActionContext, element: KtDotQualifiedExpression): Presentation? {
    val elementContext = getElementContext(context, element) ?: return null
    return Presentation.of(KotlinBundle.message("convert.to.0.as.1", elementContext.receiverText, elementContext.typeText))
  }

  override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
    if (!element.platform.isJs()) return false
    val callExpression = element.selectorExpression as? KtCallExpression ?: return false
    return callExpression.calleeExpression?.text == "unsafeCast"
  }

  override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
    val callExpression = element.selectorExpression as? KtCallExpression ?: return null

    val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val callableSymbol = call.partiallyAppliedSymbol.symbol as? KaCallableSymbol ?: return null
    val fqName = callableSymbol.importableFqName?.asString()
    if (fqName != "kotlin.js.unsafeCast") return null

    val typeArgument = callExpression.typeArguments.singleOrNull() ?: return null

    return Context(
      receiverText = element.receiverExpression.text,
      typeText = typeArgument.text
    )
  }

  override fun invoke(
    actionContext: ActionContext,
    element: KtDotQualifiedExpression,
    elementContext: Context,
    updater: ModPsiUpdater,
  ) {
    val newExpression = KtPsiFactory(element.project).createExpressionByPattern(
      "$0 as $1",
      element.receiverExpression,
      elementContext.typeText
    )
    element.replace(newExpression)
  }
}
