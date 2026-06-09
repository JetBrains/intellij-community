// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

private const val ALSO_FUNCTION_NAME = "also"
private const val APPLY_FUNCTION_NAME = "apply"
private const val FOR_EACH_FUNCTION_NAME = "forEach"
private const val ON_EACH_FUNCTION_NAME = "onEach"

internal class SimplifyNestedEachInScopeFunctionInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, SimplifyNestedEachInScopeFunctionInspection.Context>() {

    data class Context(
        val scopeFunctionName: String,
        val innerCallName: String,
        val returnsToRelabel: List<SmartPsiElementPointer<KtReturnExpression>>,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val calleeText = element.calleeExpression?.text ?: return false
        if (calleeText !in scopeFunctions) return false

        val lambdaArgument = element.valueArguments.singleOrNull() as? KtLambdaArgument ?: return false
        val lambdaExpression = lambdaArgument.getArgumentExpression()?.unpackLabelAndLambdaExpression()?.second ?: return false
        val innerExpression = lambdaExpression.bodyExpression?.statements?.singleOrNull() ?: return false
        val innerCallExpression = innerExpression.asNestedCallExpression() ?: return false
        return innerCallExpression.calleeExpression?.text in iterateFunctions
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message(
        "nested.1.call.in.0.could.be.simplified.to.2",
        context.scopeFunctionName,
        context.innerCallName,
        ON_EACH_FUNCTION_NAME,
    )

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = SimplifyNestedEachFix(context.innerCallName, context.returnsToRelabel)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val scopeFunctionName = element.getCallingShortNameOrNull(scopeFunctions) ?: return null
        val lambdaArgument = element.valueArguments.singleOrNull() as? KtLambdaArgument ?: return null
        val (labelExpression, lambdaExpression) = lambdaArgument.getArgumentExpression()?.unpackLabelAndLambdaExpression() ?: return null
        val innerExpression = lambdaExpression?.bodyExpression?.statements?.singleOrNull() ?: return null
        val innerCallExpression = innerExpression.asNestedCallExpression() ?: return null
        val innerCallName = innerCallExpression.getCallingShortNameOrNull(iterateFunctions) ?: return null
        val labelName = labelExpression?.getLabelName() ?: scopeFunctionName
        val innerLambdaBody = innerCallExpression.singleLambdaExpression()?.bodyExpression

        when (scopeFunctionName) {
            ALSO_FUNCTION_NAME -> {
                if (innerExpression !is KtDotQualifiedExpression) return null
                val receiverExpression = innerExpression.receiverExpression as? KtReferenceExpression ?: return null
                val receiverSymbol = receiverExpression.mainReference.resolveToSymbol() as? KaValueParameterSymbol ?: return null
                if (receiverSymbol.containingDeclaration?.psi != lambdaExpression.functionLiteral) return null

                if (innerLambdaBody != null) {
                    if (innerLambdaBody.referencesLabel(labelName)) return null
                    if (innerLambdaBody.referencesParameter(receiverSymbol)) return null
                }
            }

            APPLY_FUNCTION_NAME -> {
                val outerReceiverType = element.getReceiverType() ?: return null
                val innerReceiverType = innerCallExpression.getReceiverType() ?: return null
                if (!(outerReceiverType isPossiblySubTypeOf innerReceiverType)) return null

                if (innerExpression is KtDotQualifiedExpression) {
                    val receiverExpression = innerExpression.receiverExpression as? KtThisExpression ?: return null
                    val receiverLabelName = receiverExpression.getLabelName()
                    if (receiverLabelName != null && receiverLabelName != labelName) return null
                }

                if (innerLambdaBody != null) {
                    if (innerLambdaBody.referencesLabel(labelName)) return null
                    if (innerLambdaBody.referencesReceiver(lambdaExpression.functionLiteral)) return null
                }
            }

            else -> return null
        }

        val returnsToRelabel = if (innerCallName == FOR_EACH_FUNCTION_NAME) {
            collectReturnsTargetingFunctionLiteral(innerCallExpression)
        } else {
            emptyList()
        }

        return Context(scopeFunctionName, innerCallName, returnsToRelabel)
    }
}

private class SimplifyNestedEachFix(
    private val innerCallName: String,
    private val returnsToRelabel: List<SmartPsiElementPointer<KtReturnExpression>>,
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String =
        KotlinBundle.message("simplify.call.fix.text", innerCallName, ON_EACH_FUNCTION_NAME)

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.0.with.1", "nested calls", ON_EACH_FUNCTION_NAME)

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater,
    ) {
        val outerBlock = element.valueArguments.singleOrNull() ?: return
        val (_, lambda) = outerBlock.getArgumentExpression()?.unpackLabelAndLambdaExpression() ?: return
        val eachCall = when (val statement = lambda?.bodyExpression?.statements?.singleOrNull()) {
            is KtDotQualifiedExpression -> statement.selectorExpression as? KtCallExpression ?: return
            is KtCallExpression -> statement
            else -> return
        }
        val factory = KtPsiFactory(project)
        val innerBlock = eachCall.valueArguments.singleOrNull() ?: return
        if (eachCall.calleeExpression?.text == FOR_EACH_FUNCTION_NAME) {
            val writableReturns = returnsToRelabel.mapNotNull { updater.getWritable(it.element) }
            replaceReturnLabels(writableReturns, factory)
        }
        val innerBlockExpression = innerBlock.getArgumentExpression()
        if (innerBlockExpression != null && KtPsiUtil.deparenthesize(innerBlockExpression) is KtCallableReferenceExpression) {
            element.replace(factory.createExpressionByPattern("$0($1)", ON_EACH_FUNCTION_NAME, innerBlockExpression))
        } else {
            outerBlock.replace(innerBlock)
            element.calleeExpression?.replace(factory.createExpression(ON_EACH_FUNCTION_NAME))
        }
    }
}

private fun replaceReturnLabels(
    returnExpressions: List<KtReturnExpression>,
    factory: KtPsiFactory,
) {
    for (returnExpression in returnExpressions) {
        val dummyReturnExpression = factory.createExpressionByPattern("return@$0", ON_EACH_FUNCTION_NAME) as KtReturnExpression
        val newTargetLabel = dummyReturnExpression.getTargetLabel() ?: continue
        returnExpression.getTargetLabel()?.replace(newTargetLabel)
    }
}

context(_: KaSession)
private fun collectReturnsTargetingFunctionLiteral(
    callExpression: KtCallExpression,
): List<SmartPsiElementPointer<KtReturnExpression>> {
    val functionLiteral = callExpression.singleLambdaExpression()?.functionLiteral ?: return emptyList()

    return buildList {
        for (returnExpression in functionLiteral.collectDescendantsOfType<KtReturnExpression>()) {
            if (returnExpression.getLabelName() != FOR_EACH_FUNCTION_NAME) continue
            if (returnExpression.getTargetLabel()?.mainReference?.resolveToSymbol()?.psi != functionLiteral) continue
            add(returnExpression.createSmartPointer())
        }
    }
}

context(_: KaSession)
private fun KtExpression.referencesParameter(parameterSymbol: KaValueParameterSymbol): Boolean {
    var referenced = false
    accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            if (referenced) return
            if (expression.mainReference.resolveToSymbol() == parameterSymbol) {
                referenced = true
                return
            }
            super.visitSimpleNameExpression(expression)
        }
    })
    return referenced
}

context(_: KaSession)
private fun KtExpression.referencesReceiver(functionLiteral: KtFunctionLiteral): Boolean {
    var referenced = false
    accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            if (referenced) return

            val resolvedCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return
            val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol
            if (partiallyAppliedSymbol.dispatchReceiver.isReceiverFrom(functionLiteral) ||
                partiallyAppliedSymbol.extensionReceiver.isReceiverFrom(functionLiteral)
            ) {
                referenced = true
                return
            }

            super.visitSimpleNameExpression(expression)
        }

        override fun visitThisExpression(expression: KtThisExpression) {
            if (referenced) return

            if (expression.instanceReference.resolveExpression()?.containingSymbol?.psi == functionLiteral) {
                referenced = true
                return
            }

            super.visitThisExpression(expression)
        }
    })
    return referenced
}

private fun KtExpression.referencesLabel(labelName: String): Boolean {
    var referenced = false
    accept(object : KtTreeVisitorVoid() {
        override fun visitExpressionWithLabel(expression: KtExpressionWithLabel) {
            if (referenced) return
            if (expression.getLabelName() == labelName) {
                referenced = true
                return
            }
            super.visitExpressionWithLabel(expression)
        }
    })
    return referenced
}

private val scopeFunctions: Map<String, List<FqName>> =
    mapOf(ALSO_FUNCTION_NAME to listOf(StandardKotlinNames.also), APPLY_FUNCTION_NAME to listOf(StandardKotlinNames.apply))

private val iterateFunctions: Map<String, List<FqName>> = mapOf(
    FOR_EACH_FUNCTION_NAME to listOf(FqName("kotlin.collections.forEach"), FqName("kotlin.text.forEach")),
    ON_EACH_FUNCTION_NAME to listOf(FqName("kotlin.collections.onEach"), FqName("kotlin.text.onEach")),
)

context(_: KaSession)
private fun KtCallExpression.getCallingShortNameOrNull(shortNamesToFqNames: Map<String, List<FqName>>): String? {
    val shortName = calleeExpression?.text ?: return null
    val fqNames = shortNamesToFqNames[shortName] ?: return null
    val resolvedCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    return shortName.takeIf { resolvedCall.symbol.callableId?.asSingleFqName() in fqNames }
}

private fun KtExpression.asNestedCallExpression(): KtCallExpression? = when (this) {
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    is KtCallExpression -> this
    else -> null
}

private fun KtCallExpression.singleLambdaExpression(): KtLambdaExpression? =
    valueArguments.singleOrNull()?.getArgumentExpression()?.unpackLabelAndLambdaExpression()?.second

private fun KtExpression.unpackLabelAndLambdaExpression(): Pair<KtLabeledExpression?, KtLambdaExpression?> = when (this) {
    is KtLambdaExpression -> null to this
    is KtLabeledExpression -> this to baseExpression?.unpackLabelAndLambdaExpression()?.second
    is KtAnnotatedExpression -> baseExpression?.unpackLabelAndLambdaExpression() ?: (null to null)
    else -> null to null
}

context(_: KaSession)
private fun KtCallExpression.getReceiverType() =
    resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.let {
        (it.dispatchReceiver ?: it.extensionReceiver)?.type
    }

context(_: KaSession)
private fun KaReceiverValue?.isReceiverFrom(functionLiteral: KtFunctionLiteral): Boolean = when (this) {
    is KaExplicitReceiverValue -> {
        val thisExpression = KtPsiUtil.deparenthesize(expression) as? KtThisExpression
        if (thisExpression != null) {
            getThisReceiverOwner()?.psi == functionLiteral
        } else {
            expression.mainReference?.resolve() == functionLiteral
        }
    }

    is KaImplicitReceiverValue -> getThisReceiverOwner()?.psi == functionLiteral
    else -> false
}
