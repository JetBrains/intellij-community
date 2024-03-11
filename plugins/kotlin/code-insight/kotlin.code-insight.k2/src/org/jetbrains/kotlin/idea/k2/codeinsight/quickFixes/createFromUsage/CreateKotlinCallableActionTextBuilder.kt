// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtClassType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.Variance

// We must use short names of types for create-from-usage quick-fix (or IntentionAction) text.
private val RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KtTypeRenderer = KtTypeRendererForSource.WITH_SHORT_NAMES.with {
    classIdRenderer = object : KtClassTypeQualifierRenderer {
        context(KtAnalysisSession, KtTypeRenderer) override fun renderClassTypeQualifier(type: KtClassType, printer: PrettyPrinter) {
            printer.append(type.qualifiers.joinToString(separator = ".") { it.name.asString() })
        }
    }
}

/**
 * A class to build an IntentionAction text. It is used by [CreateKotlinCallableAction].
 */
internal class CreateKotlinCallableActionTextBuilder(
    private val callableKindAsString: String,
    private val nameOfUnresolvedSymbol: String,
    private val receiverExpression: KtExpression?,
    private val isAbstract: Boolean,
    private val isExtension: Boolean,
) {
    fun build(): String = buildString {
        append(KotlinBundle.message("text.create"))
        append(' ')
        append(descriptionOfCallableAsString())
        if (!endsWith(' ')) {
            append(' ')
        }
        append(callableKindAsString)

        if (nameOfUnresolvedSymbol.isNotEmpty()) {
            append(" '${renderReceiver()}$nameOfUnresolvedSymbol'")
        }
    }

    private fun descriptionOfCallableAsString(): String = when {
      isAbstract -> KotlinBundle.message("text.abstract")
      isExtension -> KotlinBundle.message("text.extension")
      hasReceiver() -> KotlinBundle.message("text.member")
      else -> ""
    }

    private fun hasReceiver() = receiverExpression != null

    private fun renderReceiver(): String {
        val receiverExpression = receiverExpression ?: return ""
        return analyze(receiverExpression) {
            val receiverSymbol = receiverExpression.resolveExpression()
            // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and
            // its type rendering.
            val type = receiverExpression.getKtType()
            val receiverTypeText = receiverSymbol?.renderAsReceiver(type) ?: type
                ?.render(RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT, Variance.INVARIANT) ?: receiverExpression.text
            if (isExtension && receiverSymbol is KtCallableSymbol) {
                val receiverType = receiverSymbol.returnType
                if (receiverType is KtFunctionalType) "($receiverTypeText)." else "$receiverTypeText."
            } else {
                receiverTypeText + if (receiverSymbol is KtClassLikeSymbol && !(receiverSymbol is KtClassOrObjectSymbol && receiverSymbol.classKind == KtClassKind.OBJECT)) ".Companion." else "."
            }
        }
    }

    context (KtAnalysisSession)
    private fun KtSymbol.renderAsReceiver(type: KtType?): String? {
        return when (this) {
            is KtCallableSymbol -> type?.selfOrSuperTypeWithAbstractMatch()
                ?.render(RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT, Variance.INVARIANT)

            is KtClassLikeSymbol -> classIdIfNonLocal?.shortClassName?.asString() ?: render(KtDeclarationRendererForSource.WITH_SHORT_NAMES)
            else -> null
        }
    }

    context (KtAnalysisSession)
    private fun KtType.selfOrSuperTypeWithAbstractMatch(): KtType? {
        if (this.hasAbstractDeclaration() == isAbstract || this is KtNonErrorClassType && (classSymbol as? KtClassOrObjectSymbol)?.classKind == KtClassKind.INTERFACE) return this
        return getDirectSuperTypes().firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch() }
    }
}