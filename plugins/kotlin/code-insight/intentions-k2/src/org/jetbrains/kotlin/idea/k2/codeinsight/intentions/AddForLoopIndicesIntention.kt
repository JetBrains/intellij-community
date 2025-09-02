// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class AddForLoopIndicesIntention :
    KotlinApplicableModCommandAction<KtForExpression, AddForLoopIndicesIntention.Context>(KtForExpression::class) {

    private val WITH_INDEX_NAME = "withIndex"
    private val WITH_INDEX_FQ_NAMES: Set<FqName> by lazy {
        sequenceOf("collections", "sequences", "text", "ranges").map { FqName("kotlin.$it.$WITH_INDEX_NAME") }.toSet()
    }

    override fun getPresentation(
        context: ActionContext,
        element: KtForExpression
    ): Presentation? {
        return super.getPresentation(context, element)?.withPriority(PriorityAction.Priority.LOW)
    }

    data class Context(val loopParameter: KtParameter)

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.indices.to.for.loop")

    override fun getApplicableRanges(element: KtForExpression): List<TextRange> {
        if (element.loopParameter == null) return emptyList()
        return listOfNotNull(TextRange(element.startOffset, element.body?.startOffset ?: element.endOffset).relativeTo(element))
    }

    override fun isApplicableByPsi(element: KtForExpression): Boolean {
        if (element.loopParameter == null) return false
        if (element.loopParameter?.destructuringDeclaration != null) return false
        return element.loopRange != null
    }

    override fun KaSession.prepareContext(element: KtForExpression): Context? {
        val loopRange = element.loopRange ?: return null
        val loopParameter = element.loopParameter ?: return null

        val resolvedCall = loopRange.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()
        if (resolvedCall in WITH_INDEX_FQ_NAMES) return null

        val psiFactory = KtPsiFactory(element.project)
        val potentialExpression = psiFactory.createExpressionCodeFragment(
            "${loopRange.text}.${WITH_INDEX_NAME}()",
            element
        ).getContentElement() ?: return null

        analyze(potentialExpression) {
            val potentialResolvedCall = potentialExpression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()
            if (potentialResolvedCall !in WITH_INDEX_FQ_NAMES) return null
        }

        return Context(loopParameter)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtForExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val loopRange = element.loopRange ?: return
        val loopParameter = elementContext.loopParameter
        val psiFactory = KtPsiFactory(element.project)

        // Replace the loop range with withIndex() call
        val newLoopRange = createWithIndexExpression(loopRange, psiFactory, true)
        loopRange.replace(newLoopRange)

        // Create a destructuring declaration for (index, item)
        var multiParameter = (psiFactory.createExpressionByPattern(
            "for((index, $0) in x){}", 
            loopParameter.text
        ) as KtForExpression).destructuringDeclaration ?: return

        multiParameter = loopParameter.replaced(multiParameter)

        val indexVariable = multiParameter.entries[0]
        updater.moveCaretTo(indexVariable.startOffset)

        val templateBuilder = updater.templateBuilder()
        templateBuilder.field(indexVariable, ChooseStringExpression(listOf("index", "i")))

        when (val body = element.body) {
            is KtBlockExpression -> {
                val statement = body.statements.firstOrNull()
                if (statement != null) {
                    templateBuilder.finishAt(statement.startOffset)
                } else {
                    body.lBrace?.endOffset?.let { templateBuilder.finishAt(it) }
                }
            }

            null -> element.rightParenthesis?.let { templateBuilder.finishAt(it.endOffset) }

            else -> templateBuilder.finishAt(body.startOffset)
        }

    }


    private fun createWithIndexExpression(originalExpression: KtExpression, psiFactory: KtPsiFactory, reformat: Boolean): KtExpression =
        psiFactory.createExpressionByPattern(
            "$0.$WITH_INDEX_NAME()",
            originalExpression,
            reformat = reformat
        )
}
