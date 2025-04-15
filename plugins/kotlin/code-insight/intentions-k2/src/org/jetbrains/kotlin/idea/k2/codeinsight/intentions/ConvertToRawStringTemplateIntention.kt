// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConvertedToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertToRawStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ConvertToRawStringTemplateIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, ConvertToRawStringTemplateIntention.Context>(KtBinaryExpression::class) {

    data class Context(
        var replacement: SmartPsiElementPointer<KtStringTemplateExpression>,
    )

    override fun getFamilyName(): String = KotlinBundle.message("convert.concatenation.to.raw.string")

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        if (!isFirstStringPlusExpressionWithoutNewLineInOperands(element)) return null
        return Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.descendantsOfType<KtStringTemplateExpression>().all { it.canBeConvertedToStringLiteral() }

    override fun invoke(
      actionContext: ActionContext,
      element: KtBinaryExpression,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        val replaced = elementContext.replacement.element?.let { element.replaced(it) } ?: return
        convertToRawStringLiteral(replaced, actionContext, updater)
    }
}