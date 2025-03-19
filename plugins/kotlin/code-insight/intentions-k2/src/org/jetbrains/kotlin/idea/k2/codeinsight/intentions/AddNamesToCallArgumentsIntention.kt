// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentNames
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.associateArgumentNamesStartingAt
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddNamesToCallArgumentsIntention :
    KotlinApplicableModCommandAction<KtCallElement, AddNamesToCallArgumentsIntention.Context>(KtCallElement::class) {

    data class Context(
        val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.names.to.call.arguments")

    override fun getApplicableRanges(element: KtCallElement): List<TextRange> {
        // Note: Applicability range matches FE 1.0 (see AddNamesToCallArgumentsIntention).
        val calleeExpression = element.calleeExpression
            ?: return emptyList()

        val calleeExpressionTextRange = calleeExpression.textRangeIn(element)
        val arguments = element.valueArguments
        return if (arguments.size < 2) {
            listOf(calleeExpressionTextRange)
        } else {
            val firstArgument = arguments.firstOrNull() as? KtValueArgument
                ?: return emptyList()
            val endOffset = firstArgument.textRangeIn(element).endOffset
            listOf(TextRange(calleeExpressionTextRange.startOffset, endOffset))
        }
    }

    override fun isApplicableByPsi(element: KtCallElement): Boolean =
        // Note: `KtCallElement.valueArgumentList` only includes arguments inside parentheses; it doesn't include a trailing lambda.
        element.valueArgumentList?.arguments?.any { !it.isNamed() } ?: false

    override fun KaSession.prepareContext(element: KtCallElement): Context? {
        val associateArgumentNamesStartingAt = associateArgumentNamesStartingAt(element, null)
        return associateArgumentNamesStartingAt?.let { Context(it) }
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallElement,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        addArgumentNames(elementContext.argumentNames.dereferenceValidKeys())
    }
}