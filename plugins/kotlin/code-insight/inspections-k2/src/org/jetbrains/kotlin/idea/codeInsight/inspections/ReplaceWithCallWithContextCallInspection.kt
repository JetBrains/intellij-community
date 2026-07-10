// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames.WITH_CALLABLE_ID
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal class ReplaceWithCallWithContextCallInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ReplaceWithCallWithContextCallInspection.ReplaceContext>() {

    data class ReplaceContext(val labeledReturns: List<KtReturnExpression>)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return file.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.parent is KtQualifiedExpression) return false
        if ((element.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() != "with") return false
        if (getLambda(element) == null) return false
        if (getReceiverArgument(element) == null) return false
        return true
    }

    override fun KaSession.prepareContext(element: KtCallExpression): ReplaceContext? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (resolvedCall.symbol.callableId != WITH_CALLABLE_ID) return null
        val lambda = getLambda(element) ?: return null
        val receiverParameter = lambda.functionLiteral.symbol.receiverParameter ?: return null

        if (!isReceiverOnlyUsedAsContextArgument(lambda, receiverParameter)) return null
        val labeledReturns = lambda.bodyExpression
            ?.collectDescendantsOfType<KtReturnExpression> { it.getLabelName() == "with" }
            .orEmpty()

        return ReplaceContext(labeledReturns)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isReceiverOnlyUsedAsContextArgument(
        lambda: KtLambdaExpression,
        receiverParameter: KaReceiverParameterSymbol,
    ): Boolean {
        val bodyExpression = lambda.bodyExpression ?: return false
        var usedAsContext = false
        val problem = bodyExpression.anyDescendantOfType<KtElement> { node ->
            when (node) {
                is KtThisExpression -> node.instanceReference.mainReference.resolveToSymbol() == receiverParameter
                is KtCallableReferenceExpression -> {
                    val appliedSymbol =
                        node.callableReference.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                    appliedSymbol?.dispatchReceiver.isReceiver(receiverParameter) ||
                            appliedSymbol?.extensionReceiver.isReceiver(receiverParameter)
                    //TODO: HERE also handle context argument when KT-73145 Callable references to declarations with context parameters will be available from 2.5
                }

                is KtSimpleNameExpression -> {
                    val appliedSymbol = node.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                    if (appliedSymbol != null) {
                        if (appliedSymbol.dispatchReceiver.isReceiver(receiverParameter)) return@anyDescendantOfType true
                        if (appliedSymbol.extensionReceiver.isReceiver(receiverParameter)) return@anyDescendantOfType true
                        if (appliedSymbol.contextArguments.any { it.isReceiver(receiverParameter) }) {
                            usedAsContext = true
                        }
                    }
                    false
                }

                else -> false
            }

        }
        return !problem && usedAsContext
    }

    private fun KaReceiverValue?.isReceiver(target: KaReceiverParameterSymbol): Boolean =
        (this as? KaImplicitReceiverValue)?.symbol == target


    override fun getProblemDescription(
        element: KtCallExpression,
        context: ReplaceContext
    ): @InspectionMessage String = KotlinBundle.message("inspection.replace.with.with.context")

    override fun createQuickFix(
        element: KtCallExpression,
        context: ReplaceContext
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.replace.with.with.context")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(project)
            context.labeledReturns.forEach { returnExpression ->
                val writableReturn = updater.getWritable(returnExpression)
                val labelName = writableReturn.getTargetLabel()?.getIdentifier() ?: return@forEach
                psiFactory.createSimpleName("context").getIdentifier()?.let { labelName.replace(it) }
            }

            val withCall = element.calleeExpression as? KtNameReferenceExpression ?: return
            withCall.replace(psiFactory.createSimpleName("context"))
        }
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        return ApplicabilityRanges.calleeExpression(element)
    }

    private fun getLambda(element: KtCallExpression): KtLambdaExpression? {
        // trailing with(x) { } form || non-trailing `with(x, { ... })` form
        return element.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: element.valueArguments.getOrNull(1)
            ?.getArgumentExpression() as? KtLambdaExpression
    }

    private fun getReceiverArgument(element: KtCallExpression): KtExpression? {
        if (element.valueArguments.size != 2) return null
        val receiverArgument = element.valueArguments.first()
        if (receiverArgument is KtLambdaArgument) return null
        if (receiverArgument.isNamed()) return null
        if (receiverArgument.getSpreadElement() != null) return null
        return receiverArgument.getArgumentExpression()
    }
}