// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.Variance

class CreateKotlinCallableActionTextBuilder(
    private val callableKindAsString: String,
    private val nameOfUnresolvedSymbol: String,
    private val receiverExpression: KtExpression?,
    private val isAbstract: Boolean,
    private val isExtension: Boolean,
) {
    fun build() = buildString {
        append(KotlinBundle.message("text.create"))
        append(' ')
        descriptionOfCallableAsString()?.let { callableKindAsString ->
            append(callableKindAsString)
            append(' ')
        }

        append(callableKindAsString)

        nameOfUnresolvedSymbol.ifEmpty { return@buildString }
        append(" '${renderReceiver()}$nameOfUnresolvedSymbol'")
    }

    private fun descriptionOfCallableAsString(): String? = if (isAbstract) {
        KotlinBundle.message("text.abstract")
    } else if (isExtension) {
        KotlinBundle.message("text.extension")
    } else if (hasReceiver()) {
        KotlinBundle.message("text.member")
    } else null

    private fun hasReceiver() = receiverExpression != null

    private fun renderReceiver(): String {
        val receiverExpression = receiverExpression ?: return ""
        return analyze(receiverExpression) {
            val receiverSymbol = receiverExpression.resolveExpression()
            // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and
            // its type rendering.
            val receiverTypeText = receiverSymbol?.renderAsReceiver() ?: receiverExpression.getKtType()
                ?.render(KtTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT) ?: receiverExpression.text
            if (isExtension && receiverSymbol is KtCallableSymbol) {
                val receiverType = receiverSymbol.returnType
                if (receiverType is KtFunctionalType) "($receiverTypeText)." else "$receiverTypeText."
            } else {
                receiverTypeText + if (receiverSymbol is KtClassLikeSymbol) ".Companion." else "."
            }
        }
    }

    context (KtAnalysisSession)
    private fun KtSymbol.renderAsReceiver(): String? = when (this) {
        is KtCallableSymbol -> returnType.selfOrSuperTypeWithAbstractMatch()
            ?.render(KtTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)

        is KtClassLikeSymbol -> classIdIfNonLocal?.shortClassName?.asString() ?: render(KtDeclarationRendererForSource.WITH_SHORT_NAMES)
        else -> null
    }

    context (KtAnalysisSession)
    private fun KtType.selfOrSuperTypeWithAbstractMatch(): KtType? {
        if (this.hasAbstractDeclaration() == isAbstract) return this
        return getDirectSuperTypes().firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch() }
    }
}