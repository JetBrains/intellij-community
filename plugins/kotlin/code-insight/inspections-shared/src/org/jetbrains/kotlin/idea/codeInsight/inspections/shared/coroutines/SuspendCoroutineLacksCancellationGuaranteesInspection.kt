// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.codeinsight.utils.qualifiedCalleeExpressionTextRange
import org.jetbrains.kotlin.idea.codeinsight.utils.qualifiedCalleeExpressionTextRangeInThis
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.refactoring.parentLabeledExpression
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

/**
 * Inspection that suggests replacing kotlin.coroutines.suspendCoroutine with
 * kotlinx.coroutines.suspendCancellableCoroutine when kotlinx.coroutines is available.
 */
internal class SuspendCoroutineLacksCancellationGuaranteesInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, SuspendCoroutineLacksCancellationGuaranteesInspection.Context>() {

    class Context(
        val labelReferencesToUpdate: List<KtLabelReferenceExpression>
    )

    override fun getProblemDescription(element: KtCallExpression, context: Context): @InspectionMessage String {
        return KotlinBundle.message("inspection.suspend.coroutine.lacks.cancellation.guarantees.description")
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.getCallNameExpression()?.getReferencedNameAsName() == SUSPEND_COROUTINE_ID.callableName

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val functionCall = element.resolveToCall()?.successfulFunctionCallOrNull()

        val calledFunction = functionCall?.symbol ?: return null
        if (calledFunction.callableId != SUSPEND_COROUTINE_ID) return null

        if (!CoroutinesIds.SUSPEND_CANCELLABLE_COROUTINE_ID.canBeResolved()) return null

        val singlePassedArgument = functionCall.argumentMapping.keys.single()

        val labelReferencesToUpdate = when (singlePassedArgument) {
            // case of lambda with no custom label
            is KtLambdaExpression if (singlePassedArgument.parentLabeledExpression() == null) -> {
                singlePassedArgument.functionLiteral.findRelatedLabelReferences()
            }

            // case of anonymous function with no custom label
            is KtNamedFunction -> singlePassedArgument.findRelatedLabelReferences()

            // all other possible arguments; custom labels (if present) do not need to be changed
            else -> emptyList()
        }

        return Context(labelReferencesToUpdate)
    }

    override fun createQuickFix(element: KtCallExpression, context: Context): KotlinModCommandQuickFix<KtCallExpression> {
        return object : KotlinModCommandQuickFix<KtCallExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("inspection.suspend.coroutine.lacks.cancellation.guarantees.fix")

            override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
                val labeledReturn = KtPsiFactory(project).createExpression(
                    "return@${CoroutinesIds.SUSPEND_CANCELLABLE_COROUTINE_ID.callableName}"
                ) as KtReturnExpression
                val updatedLabeledExpression = labeledReturn.getTargetLabel() ?: return

                val writableLabelReferencesToUpdate = context.labelReferencesToUpdate.mapNotNull { updater.getWritable(it) }
                writableLabelReferencesToUpdate.forEach {
                    it.replace(updatedLabeledExpression)
                }

                val updatedCall = element.replacePossiblyQualifiedCallee(CoroutinesIds.SUSPEND_CANCELLABLE_COROUTINE_ID) ?: return
                val updatedCallQualifierRangeInFile = updatedCall.qualifiedCalleeExpressionTextRange ?: return

                ShortenReferencesFacility.getInstance().shorten(updatedCall.containingKtFile, updatedCallQualifierRangeInFile)
            }
        }
    }
}

private val SUSPEND_COROUTINE_ID: CallableId = CallableId(FqName("kotlin.coroutines"), Name.identifier("suspendCoroutine"))

private fun KtCallExpression.replacePossiblyQualifiedCallee(newCallable: CallableId): KtDotQualifiedExpression? {
    val originalCall = this.getQualifiedExpressionForSelectorOrThis()
    val qualifiedCalleeRange = originalCall.qualifiedCalleeExpressionTextRangeInThis ?: return null

    val replacementCallText = qualifiedCalleeRange.replace(originalCall.text, newCallable.asSingleFqName().asString())
    val replacementCall = KtPsiFactory(this.project).createExpression(replacementCallText)

    // we have to replace the whole call, 
    // because otherwise we might get malformed PSI
    return originalCall.replace(replacementCall) as? KtDotQualifiedExpression
}
