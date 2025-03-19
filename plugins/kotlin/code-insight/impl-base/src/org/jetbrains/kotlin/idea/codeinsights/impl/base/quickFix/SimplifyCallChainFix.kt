// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class SimplifyCallChainFix(
    private val conversion: CallChainConversion,
    private val modifyArguments: KtPsiFactory.(KtCallExpression) -> Unit = {}
) : KotlinModCommandQuickFix<KtQualifiedExpression>() {
    private val shortenedText = conversion.replacement.substringAfterLast(".")

    override fun getName(): String = KotlinBundle.message("simplify.call.chain.fix.text", shortenedText)

    override fun getFamilyName(): String = name

    fun apply(qualifiedExpression: KtQualifiedExpression) {
        val psiFactory = KtPsiFactory(qualifiedExpression.project)
        val firstExpression = qualifiedExpression.receiverExpression

        val operationSign = when (firstExpression) {
            is KtSafeQualifiedExpression -> "?."
            is KtQualifiedExpression -> "."
            else -> ""
        }

        val receiverExpressionOrEmptyString = if (firstExpression is KtQualifiedExpression) firstExpression.receiverExpression.text else ""

        val firstCallExpression = CallChainExpressions.getFirstCallExpression(firstExpression) ?: return
        psiFactory.modifyArguments(firstCallExpression)
        val firstCallArgumentList = firstCallExpression.valueArgumentList

        val secondCallExpression = qualifiedExpression.selectorExpression as? KtCallExpression ?: return
        val secondCallArgumentList = secondCallExpression.valueArgumentList
        val secondCallTrailingComma = secondCallArgumentList?.trailingComma
        secondCallTrailingComma?.delete()

        fun KtValueArgumentList.getTextInsideParentheses(): String {
            val range = PsiChildRange(leftParenthesis?.nextSibling ?: firstChild, rightParenthesis?.prevSibling ?: lastChild)
            return range.joinToString(separator = "") { it.text }
        }

        val lambdaExpression = firstCallExpression.lambdaArguments.singleOrNull()?.getLambdaExpression()
        val additionalArgument = conversion.additionalArgument
        val secondCallHasArguments = secondCallArgumentList?.arguments?.isNotEmpty() == true
        val firstCallHasArguments = firstCallArgumentList?.arguments?.isNotEmpty() == true
        val argumentsText = listOfNotNull(
            secondCallArgumentList.takeIf { secondCallHasArguments }?.getTextInsideParentheses(),
            firstCallArgumentList.takeIf { firstCallHasArguments }?.getTextInsideParentheses(),
            additionalArgument.takeIf { !firstCallHasArguments && !secondCallHasArguments },
            lambdaExpression?.text
        ).joinToString(separator = ",")

        val newCallText = conversion.replacement
        val newQualifiedOrCallExpression = psiFactory.createExpression(
            "$receiverExpressionOrEmptyString$operationSign$newCallText($argumentsText)"
        )

        var result = qualifiedExpression.replaced(newQualifiedOrCallExpression)

        if (lambdaExpression != null || additionalArgument != null) {
            val callExpression = when (result) {
                is KtQualifiedExpression -> result.callExpression
                is KtCallExpression -> result
                else -> null
            }
            callExpression?.moveFunctionLiteralOutsideParentheses()
        }
        if (secondCallTrailingComma != null && !firstCallHasArguments) {
            val call = result.safeAs<KtQualifiedExpression>()?.callExpression ?: result.safeAs<KtCallExpression>()
            call?.valueArgumentList?.arguments?.lastOrNull()?.add(psiFactory.createComma())
        }
        if (conversion.addNotNullAssertion) {
            result = result.replaced(psiFactory.createExpressionByPattern("$0!!", result))
        }
        if (conversion.removeNotNullAssertion) {
            val parent = result.parent
            if (parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL) {
                result = parent.replaced(result)
            }
        }

        result.containingKtFile.commitAndUnblockDocument()
        @OptIn(KaIdeApi::class)
        if (result.isValid) shortenReferences(result.reformatted() as KtElement)
    }

    override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {
        apply(element)
    }
}