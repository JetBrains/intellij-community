// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*


class RemoveForLoopIndicesIntention :
    KotlinApplicableModCommandAction<KtForExpression, RemoveForLoopIndicesIntention.Context>(KtForExpression::class) {
    private val WITH_INDEX_NAME = "withIndex"
    private val WITH_INDEX_FQ_NAMES: Set<FqName> by lazy {
        sequenceOf("collections", "sequences", "text", "ranges").map { FqName("kotlin.$it.$WITH_INDEX_NAME") }.toSet()
    }

    data class Context(val elementVar: KtDestructuringDeclarationEntry)

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.indices.in.for.loop")

    override fun getApplicableRanges(element: KtForExpression): List<TextRange> {
        val indexVar = getIndexVar(element) ?: return emptyList()
        return listOfNotNull(indexVar.nameIdentifier?.range?.relativeTo(element))
    }

    override fun isApplicableByPsi(element: KtForExpression): Boolean {
        val multiParameter = element.destructuringDeclaration ?: return false
        return multiParameter.entries.size == 2
    }

    override fun KaSession.prepareContext(element: KtForExpression): Context? {
        val loopRange = element.loopRange as? KtDotQualifiedExpression ?: return null
        val multiParameter = element.destructuringDeclaration ?: return null

        val functionCall = loopRange.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName() ?: return null
        if (functionCall !in WITH_INDEX_FQ_NAMES) return null
        val indexVar = multiParameter.entries[0]
        if (ReferencesSearch.search(indexVar).any()) return null

        val elementVar = multiParameter.entries[1]
        return Context(elementVar)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtForExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val loopRange = element.loopRange as KtDotQualifiedExpression

        val psiFactory = KtPsiFactory(element.project)
        val loop = psiFactory.createExpressionByPattern("for ($0 in _) {}", elementContext.elementVar.text) as KtForExpression

        loop.loopParameter?.let { element.loopParameter?.replace(it) }
        loopRange.replace(loopRange.receiverExpression)
    }

    private fun getIndexVar(element: KtForExpression): KtDestructuringDeclarationEntry? {
        val multiParameter = element.destructuringDeclaration ?: return null
        if (multiParameter.entries.size != 2) return null
        return multiParameter.entries[0]
    }
}
