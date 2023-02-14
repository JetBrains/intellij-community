// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.stubs.ConstantValueKind
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType

class AddUnderscoresToNumericLiteralIntention : SelfTargetingIntention<KtConstantExpression>(
    KtConstantExpression::class.java, KotlinBundle.lazyMessage("add.underscores")
) {
    override fun isApplicableTo(element: KtConstantExpression, caretOffset: Int): Boolean {
        val text = element.text
        return element.isNumeric() && !text.hasUnderscore() && text.takeWhile { it.isDigit() }.length > 3
    }

    override fun applyTo(element: KtConstantExpression, editor: Editor?) {
        val text = element.text
        val digits = text.takeWhile { it.isDigit() }
        element.replace(
            KtPsiFactory(element.project).createExpression(
                digits.reversed().chunked(3).joinToString(separator = "_").reversed() + text.removePrefix(digits)
            )
        )
    }
}

class RemoveUnderscoresFromNumericLiteralIntention : SelfTargetingIntention<KtConstantExpression>(
    KtConstantExpression::class.java, KotlinBundle.lazyMessage("remove.underscores")
) {
    override fun isApplicableTo(element: KtConstantExpression, caretOffset: Int): Boolean =
        element.isNumeric() && element.text.hasUnderscore()

    override fun applyTo(element: KtConstantExpression, editor: Editor?) {
        element.replace(KtPsiFactory(element.project).createExpression(element.text.replace("_", "")))
    }
}

private fun KtConstantExpression.isNumeric(): Boolean = elementType in numericConstantKinds

private val numericConstantKinds = listOf(
    KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.INTEGER_CONSTANT),
    KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.FLOAT_CONSTANT)
)

private fun String.hasUnderscore(): Boolean = indexOf('_') != -1