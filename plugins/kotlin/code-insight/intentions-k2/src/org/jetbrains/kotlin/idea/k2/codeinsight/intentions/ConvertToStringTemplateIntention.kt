// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.containNoNewLine
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A class for convert-to-string-template intention.
 *
 * Example: "a" + 1 + 'b' + foo + 2.3f + bar -> "a1b${foo}2.3f{bar}"
 */
internal class ConvertToStringTemplateIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, ConvertToStringTemplateIntention.Context>(KtBinaryExpression::class) {

    data class Context(
        val replacement: SmartPsiElementPointer<KtStringTemplateExpression>,
    )

    override fun getFamilyName(): String = KotlinBundle.message("convert.concatenation.to.template")

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.operationToken == KtTokens.PLUS && element.containNoNewLine()

    /**
     * [element] is applicable for this intention if
     * - it is an expression with only string plus operations, and
     * - its parent is not an expression with only string plus operations
     *   - which helps us to avoid handling the child multiple times
     *     e.g., for "a" + 'b' + "c", we do not want to visit both 'b' + "c" and "a" + 'b' + "c" since 'b' + "c" will be handled
     *     in "a" + 'b' + "c".
     */
    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? =
        if (isFirstStringPlusExpressionWithoutNewLineInOperands(element))
            Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
        else
            null

    override fun invoke(
      actionContext: ActionContext,
      element: KtBinaryExpression,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        elementContext.replacement.element?.let { element.replaced(updater.getWritable(it)) }
    }
}