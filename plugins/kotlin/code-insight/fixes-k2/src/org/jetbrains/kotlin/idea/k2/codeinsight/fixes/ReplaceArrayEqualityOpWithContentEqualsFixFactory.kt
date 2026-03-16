// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal object ReplaceArrayEqualityOpWithContentEqualsFixFactory {

    val fixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArrayEqualityOperatorCanBeReplacedWithContentEquals ->
            val binaryExpression = diagnostic.psi as? KtBinaryExpression ?: return@ModCommandBased emptyList()
            val elementContext = when (binaryExpression.operationToken) {
                KtTokens.EXCLEQ -> ElementContext(isNotEqualOperator = true)
                KtTokens.EQEQ -> ElementContext(isNotEqualOperator = false)
                else -> return@ModCommandBased emptyList()
            }
            listOf(ReplaceWithContentEqualsFix(binaryExpression, elementContext))
        }

    @JvmInline
    private value class ElementContext(val isNotEqualOperator: Boolean)

    private class ReplaceWithContentEqualsFix(
        element: KtBinaryExpression,
        private val operatorContext: ElementContext,
    ) : PsiUpdateModCommandAction<KtBinaryExpression>(element) {

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.content.equals")

        override fun getPresentation(
            context: ActionContext,
            element: KtBinaryExpression,
        ): Presentation {
            val actionName = if (operatorContext.isNotEqualOperator) {
                KotlinBundle.message("replace.not.equal.with.content.equals")
            } else {
                KotlinBundle.message("replace.equal.with.content.equals")
            }
            return Presentation.of(actionName)
        }

        override fun invoke(
            actionContext: ActionContext,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            val left = element.left ?: return
            val right = element.right ?: return
            val template = buildString {
                if (operatorContext.isNotEqualOperator) append("!")
                append("$0.contentEquals($1)")
            }
            val factory = KtPsiFactory(element.project)
            element.replace(factory.createExpressionByPattern(template, left, right))
        }
    }
}
