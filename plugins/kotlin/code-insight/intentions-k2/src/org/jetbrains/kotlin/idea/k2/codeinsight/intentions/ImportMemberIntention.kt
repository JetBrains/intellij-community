// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.idea.base.analysis.api.utils.invokeShortening
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective

internal class ImportMemberIntention :
    AbstractKotlinModCommandWithContext<KtNameReferenceExpression, ImportMemberIntention.Context>(KtNameReferenceExpression::class),
    HighPriorityAction {

    class Context(
        val fqName: FqName,
        val shortenCommand: ShortenCommand,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.import.for.member")

    override fun getActionName(element: KtNameReferenceExpression, context: Context): String =
        KotlinBundle.message("add.import.for.0", context.fqName.asString())

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtNameReferenceExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtNameReferenceExpression): Boolean =
        // Ignore simple name expressions or already imported names.
        element.getQualifiedElement() != element && !element.isInImportDirective()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtNameReferenceExpression): Context? {
        val symbol = element.mainReference.resolveToSymbol() ?: return null
        return computeContext(element, symbol)
    }

    override fun apply(element: KtNameReferenceExpression, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        context.analyzeContext.shortenCommand.invokeShortening()
    }
}

context(KtAnalysisSession)
private fun computeContext(psi: KtNameReferenceExpression, symbol: KtSymbol): ImportMemberIntention.Context? {
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
                classShortenStrategy = {
                    if (it.classIdIfNonLocal == classId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                }, callableShortenStrategy = {
                    if (it is KtConstructorSymbol && it.containingClassIdIfNonLocal == classId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                })
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Context(classId.asSingleFqName(), shortenCommand)
        }

        is KtCallableSymbol -> {
            val callableId = symbol.callableIdIfNonLocal ?: return null
            if (callableId.callableName.isSpecial) return null
            if (!canBeImported(symbol)) return null
            val shortenCommand = collectPossibleReferenceShortenings(
                psi.containingKtFile,
                classShortenStrategy = { ShortenStrategy.DO_NOT_SHORTEN },
                callableShortenStrategy = {
                    if (it.callableIdIfNonLocal == callableId)
                        ShortenStrategy.SHORTEN_AND_IMPORT
                    else
                        ShortenStrategy.DO_NOT_SHORTEN
                }
            )
            if (shortenCommand.isEmpty) return null
            ImportMemberIntention.Context(callableId.asSingleFqName(), shortenCommand)
        }

        else -> return null
    }
}

context(KtAnalysisSession)
private fun canBeImported(symbol: KtCallableSymbol): Boolean {
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