// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.stubs.ConstantValueKind
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType

internal class AddUnderscoresToNumericLiteralIntention : KotlinApplicableModCommandAction<KtConstantExpression, Unit>(
    KtConstantExpression::class
) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.underscores")

    override fun isApplicableByPsi(element: KtConstantExpression): Boolean {
        val text = element.text
        return element.isNumeric() && !text.hasUnderscore() && text.takeWhile { it.isDigit() }.length > 3
    }

    override fun KaSession.prepareContext(element: KtConstantExpression): Unit = Unit

    override fun invoke(
        actionContext: ActionContext,
        element: KtConstantExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val text = element.text
        val digits = text.takeWhile { it.isDigit() }
        element.replace(
            KtPsiFactory(element.project).createExpression(
                digits.reversed().chunked(3).joinToString(separator = "_").reversed() + text.removePrefix(digits)
            )
        )
    }
}

internal class RemoveUnderscoresFromNumericLiteralIntention : KotlinApplicableModCommandAction<KtConstantExpression, Unit>(
    KtConstantExpression::class
) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.underscores")

    override fun isApplicableByPsi(element: KtConstantExpression): Boolean =
        element.isNumeric() && element.text.hasUnderscore()

    override fun KaSession.prepareContext(element: KtConstantExpression): Unit = Unit

    override fun invoke(
        actionContext: ActionContext,
        element: KtConstantExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(element.project).createExpression(element.text.replace("_", "")))
    }
}

private fun KtConstantExpression.isNumeric(): Boolean = elementType in numericConstantKinds

private val numericConstantKinds = listOf(
    KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.INTEGER_CONSTANT),
    KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.FLOAT_CONSTANT)
)

private fun String.hasUnderscore(): Boolean = indexOf('_') != -1