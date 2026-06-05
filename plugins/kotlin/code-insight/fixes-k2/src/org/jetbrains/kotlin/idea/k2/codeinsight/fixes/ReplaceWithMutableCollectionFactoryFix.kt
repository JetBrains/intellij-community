// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.getExpressionShortText
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix that replaces an immutable collection creation call with its mutable variant.
 *
 * For example:
 * - `emptyList()` → `mutableListOf()`
 * - `listOf(1, 2)` → `mutableListOf(1, 2)`
 * - `setOf("a")` → `mutableSetOf("a")`
 */
internal class ReplaceWithMutableCollectionFactoryFix(
    element: KtCallExpression,
    private val replacementFunctionName: String,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.mutable.collection")

    override fun getActionPresentation(
        context: ActionContext,
        element: KtCallExpression,
    ): Presentation? {
        val originalText = getExpressionShortText(element)

        val copy = element.copied()
        copy.replaceWithMutableFactory() ?: return null
        val replacementText = getExpressionShortText(copy)

        val actionName = KotlinBundle.message("replace.0.with.1", originalText, replacementText)
        return Presentation.of(actionName).withPriority(PriorityAction.Priority.HIGH)
    }

    override fun invoke(
        context: ActionContext,
        element: KtCallExpression,
        updater: ModPsiUpdater,
    ) {
        element.replaceWithMutableFactory()
    }

    private fun KtCallExpression.replaceWithMutableFactory(): KtCallExpression? {
        val calleeExpression = calleeExpression ?: return null
        val psiFactory = KtPsiFactory(project)
        calleeExpression.replace(psiFactory.createExpression(replacementFunctionName))
        return this
    }
}
