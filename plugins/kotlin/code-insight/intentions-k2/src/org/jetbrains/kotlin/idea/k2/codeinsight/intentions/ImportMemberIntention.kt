// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective

internal class ImportMemberIntention :
    AbstractKotlinApplicatorBasedIntention<KtNameReferenceExpression, ImportMemberIntention.Input>(KtNameReferenceExpression::class),
    HighPriorityAction {
    class Input(val fqName: FqName, val shortenCommand: ShortenCommand) : KotlinApplicatorInput

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider() = inputProvider { psi: KtNameReferenceExpression ->
        val symbol = psi.mainReference.resolveToSymbol() ?: return@inputProvider null
        computeInput(psi, symbol)
    }

    override fun getApplicator() = applicator<KtNameReferenceExpression, Input> {
        familyName(KotlinBundle.lazyMessage("add.import.for.member"))
        actionName { _, input -> KotlinBundle.message("add.import.for.0", input.fqName.asString()) }
        isApplicableByPsi {
            // Ignore simple name expressions or already imported names.
            if (it.getQualifiedElement() == it || it.isInImportDirective()) return@isApplicableByPsi false
            true
        }
        applyTo { _, input ->
            input.shortenCommand.invokeShortening()
        }
    }


}

private fun KtAnalysisSession.computeInput(psi: KtNameReferenceExpression, symbol: KtSymbol): ImportMemberIntention.Input? {
    return when (symbol) {
        is KtConstructorSymbol,
        is KtClassOrObjectSymbol -> {
            val classId = if (symbol is KtClassOrObjectSymbol) {
                symbol.classIdIfNonLocal
            } else {
                (symbol as KtConstructorSymbol).containingClassIdIfNonLocal
            } ?: return null
            val shortenCommand = collectPossibleReferenceShortenings(
                psi.containingKtFile,
                classShortenOption = {
                    if (it.classIdIfNonLocal == classId)
                        ShortenOption.SHORTEN_AND_IMPORT
                    else
                        ShortenOption.DO_NOT_SHORTEN
                }, callableShortenOption = {
                    if (it is KtConstructorSymbol && it.containingClassIdIfNonLocal == classId)
                        ShortenOption.SHORTEN_AND_IMPORT
                    else
                        ShortenOption.DO_NOT_SHORTEN
                })
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Input(classId.asSingleFqName(), shortenCommand)
        }

        is KtCallableSymbol -> {
            val callableId = symbol.callableIdIfNonLocal ?: return null
            if (callableId.callableName.isSpecial) return null
            if (!canBeImported(symbol)) return null
            val shortenCommand = collectPossibleReferenceShortenings(
                psi.containingKtFile,
                classShortenOption = { ShortenOption.DO_NOT_SHORTEN },
                callableShortenOption = {
                    if (it.callableIdIfNonLocal == callableId)
                        ShortenOption.SHORTEN_AND_IMPORT
                    else
                        ShortenOption.DO_NOT_SHORTEN
                }
            )
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Input(callableId.asSingleFqName(), shortenCommand)
        }

        else -> return null
    }
}

private fun KtAnalysisSession.canBeImported(symbol: KtCallableSymbol): Boolean {
    if (symbol is KtEnumEntrySymbol) return true
    if (symbol.origin == KtSymbolOrigin.JAVA) {
        return when (symbol) {
            is KtFunctionSymbol -> symbol.isStatic
            is KtPropertySymbol -> symbol.isStatic
            is KtJavaFieldSymbol -> symbol.isStatic
            else -> false
        }
    } else {
        if ((symbol as? KtSymbolWithKind)?.symbolKind == KtSymbolKind.TOP_LEVEL) return true
        val containingClass = symbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return true
        return containingClass.classKind == KtClassKind.OBJECT || containingClass.classKind == KtClassKind.COMPANION_OBJECT
    }
}