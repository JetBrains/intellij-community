// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.enhancedTypeOrSelf
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isBooleanType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class NullableBooleanEqualityCheckToElvisIntention : KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(KtBinaryExpression::class) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val equality = element.operationToken == KtTokens.EQEQ
        val constPart = element.left as? KtConstantExpression ?: element.right as? KtConstantExpression ?: return
        val exprPart = (if (element.right == constPart) element.left else element.right) ?: return
        val constValue = when {
            KtPsiUtil.isTrueConstant(constPart) -> true
            KtPsiUtil.isFalseConstant(constPart) -> false
            else -> return
        }

        val psiFactory = KtPsiFactory(constPart.project)
        val elvis = psiFactory.createExpressionByPattern("$0 ?: ${!constValue}", exprPart)
        element.replaced(if (constValue == equality) elvis else psiFactory.createExpressionByPattern("!($0)", elvis))
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.boolean.const.to.elvis")

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        if (element.operationToken != KtTokens.EQEQ && element.operationToken != KtTokens.EXCLEQ) return null
        val lhs = element.left ?: return null
        val rhs = element.right ?: return null

        return if (isApplicable(lhs, rhs) || isApplicable(rhs, lhs)) Unit else null
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun isApplicable(lhs: KtExpression, rhs: KtExpression): Boolean {
        if (!KtPsiUtil.isBooleanConstant(rhs)) return false

        val expressionType = lhs.expressionType ?: return false

        return expressionType.isNullable && (expressionType.enhancedTypeOrSelf?.isBooleanType == true)
    }
}
