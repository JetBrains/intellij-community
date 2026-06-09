// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.hasOrOverridesCallableId
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isZeroIntegerConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.getTopmostParenthesizedExpressionOrSelf
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.binaryExpressionVisitor
import org.jetbrains.kotlin.psi.buildExpression

private val HASH_CODE_CALLABLE_ID = CallableId(StandardClassIds.Any, StandardNames.HASHCODE_NAME)

internal class NullableHashCodeInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        binaryExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun getProblemDescription(element: KtBinaryExpression, context: Unit): String =
        KotlinBundle.message("inspection.nullable.hash.code.display.name")

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> {
        val hashCodeCallee = (element.getHashCodeSafeQualifiedExpression()?.selectorExpression as? KtCallExpression)?.calleeExpression
        return listOfNotNull(hashCodeCallee?.textRange?.shiftLeft(element.textRange.startOffset))
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.ELVIS) return false
        if (element.right?.isZeroIntegerConstant != true) return false
        return element.getHashCodeSafeQualifiedExpression() != null
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        val safeQualifiedExpression = element.getHashCodeSafeQualifiedExpression() ?: return null
        val receiverType = safeQualifiedExpression.receiverExpression.expressionType ?: return null
        if (!receiverType.isNullable) return null

        val callExpression = safeQualifiedExpression.selectorExpression as? KtCallExpression ?: return null
        val functionSymbol = callExpression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
        return functionSymbol.hasOrOverridesCallableId(HASH_CODE_CALLABLE_ID).asUnit
    }

    override fun createQuickFix(element: KtBinaryExpression, context: Unit): KotlinModCommandQuickFix<KtBinaryExpression> =
        object : KotlinModCommandQuickFix<KtBinaryExpression>() {
            override fun getFamilyName(): String =
                KotlinBundle.message("inspection.nullable.hash.code.action.name")

            override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
                val receiver = element.getHashCodeSafeQualifiedExpression()?.receiverExpression ?: return
                val targetExpression = element.getTopmostParenthesizedExpressionOrSelf()
                val commentSaver = CommentSaver(targetExpression)
                val newElement = KtPsiFactory(project).buildExpression {
                    appendExpression(receiver)
                    appendFixedText(".hashCode()")
                }
                val replaced = targetExpression.replaced(newElement)
                commentSaver.restore(replaced)
            }
        }
}

private fun KtBinaryExpression.getHashCodeSafeQualifiedExpression(): KtSafeQualifiedExpression? {
    val lhsExpression = left ?: return null
    val lhs = KtPsiUtil.safeDeparenthesize(lhsExpression) as? KtSafeQualifiedExpression ?: return null
    val callExpression = lhs.selectorExpression as? KtCallExpression ?: return null
    if (callExpression.calleeExpression?.text != StandardNames.HASHCODE_NAME.asString()) return null
    if (callExpression.valueArguments.isNotEmpty()) return null
    if (callExpression.lambdaArguments.isNotEmpty()) return null

    return lhs
}
