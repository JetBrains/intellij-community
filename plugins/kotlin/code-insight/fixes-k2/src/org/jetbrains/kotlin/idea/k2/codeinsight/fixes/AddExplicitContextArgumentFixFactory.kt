// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommand.chooseAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.getRemainingArgumentsData
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.contextSuggestedNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

internal object AddExplicitContextArgumentFixFactory {

    private class CandidateInfo(
        val remainingArgumentsData: RemainingArgumentsData,
        val displayText: String,
        val nameSuggestions: Map<Name, Name>
    )

    @OptIn(KaExperimentalApi::class)
    val overloadResolutionAmbiguity = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.OverloadResolutionAmbiguity ->
        val psi = diagnostic.psi
        val callExpression = (psi as? KtCallExpression)
            ?: psi.getStrictParentOfType<KtCallExpression>()
            ?: return@ModCommandBased emptyList()

        if (callExpression.valueArgumentList == null) return@ModCommandBased emptyList()

        if (!callExpression.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)) {
            return@ModCommandBased emptyList()
        }

        val resolvedCallCandidates = callExpression.resolveToCallCandidates()
        if (resolvedCallCandidates.size < 2) return@ModCommandBased emptyList()
        val existingArgumentsCount = callExpression.valueArguments.size

        val candidateInfos = resolvedCallCandidates.filter { it.isInBestCandidates }
            .mapNotNull { callCandidateInfo ->
                val candidateCall = callCandidateInfo.candidate as? KaFunctionCall<*> ?: return@mapNotNull null
                val symbol = candidateCall.symbol
                if (!symbol.hasStableParameterNames) return@mapNotNull null

                val remainingArguments = candidateCall.getRemainingArgumentsData(existingArgumentsCount) ?: return@mapNotNull null

                if (remainingArguments.allContextRemainingArguments.isEmpty()) return@mapNotNull null
                val functionSymbol = symbol as? KaNamedFunctionSymbol ?: return@mapNotNull null

                val description = functionSymbol.contextParameters.joinToString { parameter ->
                    "${parameter.name}: ${
                        parameter.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
                    }"
                }

                val nameSuggestions: Map<Name, Name> = contextSuggestedNames(candidateCall, remainingArguments, callExpression)
                CandidateInfo(remainingArguments, description, nameSuggestions)
            }

        if (candidateInfos.isEmpty()) return@ModCommandBased emptyList()

        val fix = if (candidateInfos.size == 1) {
            AddExplicitContextArgumentFix(callExpression, candidateInfos.single(), isStandalone = true)
        } else {
            AddExplicitContextArgumentChooserFix(callExpression, candidateInfos)
        }
        listOf(fix)
    }

    private class AddExplicitContextArgumentChooserFix(
        element: KtCallExpression,
        private val candidates: List<CandidateInfo>
    ) : PsiBasedModCommandAction<KtCallExpression>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("fix.add.explicit.context.argument.family")

        override fun perform(context: ActionContext, element: KtCallExpression): ModCommand {
            val fixes = candidates.map { AddExplicitContextArgumentFix(element, it) }
            return chooseAction(KotlinBundle.message("fix.add.explicit.context.argument.chooser.title"), fixes)
        }
    }

    private class AddExplicitContextArgumentFix(
        element: KtCallExpression,
        private val candidateInfo: CandidateInfo,
        private val isStandalone: Boolean = false
    ) : PsiUpdateModCommandAction<KtCallExpression>(element) {

        override fun getFamilyName(): String =
            KotlinBundle.message("fix.add.explicit.context.argument.family")

        override fun getPresentation(context: ActionContext, element: KtCallExpression): Presentation =
            if (isStandalone) {
                Presentation.of(KotlinBundle.message("fix.add.explicit.context.argument.family.single", candidateInfo.displayText))
            } else {
                Presentation.of(KotlinBundle.message("fix.add.explicit.context.argument.for", candidateInfo.displayText))
            }

        override fun invoke(context: ActionContext, element: KtCallExpression, updater: ModPsiUpdater) {
            val argumentList = element.valueArgumentList ?: return
            val remainingArgumentsData = candidateInfo.remainingArgumentsData
            SpecifyRemainingArgumentsByNameUtil.applyFix(
                context.project,
                argumentList,
                emptyList(),
                remainingArgumentsData.allContextRemainingArguments,
                remainingArgumentsData.allContextParameterNames,
                updater,
                candidateInfo.nameSuggestions
            )
        }
    }
}
