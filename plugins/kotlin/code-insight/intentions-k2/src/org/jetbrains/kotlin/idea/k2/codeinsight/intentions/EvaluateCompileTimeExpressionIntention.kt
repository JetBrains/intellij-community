// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class EvaluateCompileTimeExpressionIntention : KotlinApplicableModCommandAction<KtBinaryExpression, String>(KtBinaryExpression::class) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpression,
        elementContext: String,
        updater: ModPsiUpdater
    ) {
        element.replace(KtPsiFactory(element.project).createExpression(elementContext))
    }

    override fun getPresentation(
        context: ActionContext,
        element: KtBinaryExpression
    ): Presentation? {
        val constantValue = getElementContext(context, element) ?: return null
        return Presentation.of(KotlinBundle.message("replace.with.0", constantValue))
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("evaluate.compile.time.expression")

    override fun KaSession.prepareContext(element: KtBinaryExpression): String? {
        val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(element.language)
        val value = expressionEvaluator.computeConstantExpression(element, false) ?: return null
        return when (value) {
            is Char -> "'${StringUtil.escapeStringCharacters(value.toString())}'"
            is Long -> "${value}L"
            is Float -> when {
                value.isNaN() -> "Float.NaN"
                value.isInfinite() -> if (value > 0.0f) "Float.POSITIVE_INFINITY" else "Float.NEGATIVE_INFINITY"
                else -> "${value}f"
            }
            is Double -> when {
                value.isNaN() -> "Double.NaN"
                value.isInfinite() -> if (value > 0.0) "Double.POSITIVE_INFINITY" else "Double.NEGATIVE_INFINITY"
                else -> value.toString()
            }
            else -> value.toString()
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        return element.getStrictParentOfType<KtBinaryExpression>() == null && element.isConstantExpression()
    }

    private fun KtExpression?.isConstantExpression(): Boolean {
        return when (val expression = KtPsiUtil.deparenthesize(this)) {
            is KtConstantExpression -> expression.elementType in constantNodeTypes
            is KtPrefixExpression -> expression.baseExpression.isConstantExpression()
            is KtBinaryExpression -> expression.left.isConstantExpression() && expression.right.isConstantExpression()
            else -> false
        }
    }

    private val constantNodeTypes: List<IElementType> = listOf(
        KtNodeTypes.FLOAT_CONSTANT,
        KtNodeTypes.CHARACTER_CONSTANT,
        KtNodeTypes.INTEGER_CONSTANT
    )
}
