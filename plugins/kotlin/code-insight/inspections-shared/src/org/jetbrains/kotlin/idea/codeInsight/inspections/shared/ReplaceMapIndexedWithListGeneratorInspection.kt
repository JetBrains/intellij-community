// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasUsages
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ReplaceMapIndexedWithListGeneratorInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ReplaceMapIndexedWithListGeneratorInspection.Context>() {

    @JvmInline
    value class Context(val labeledReturnExpressions: List<SmartPsiElementPointer<KtReturnExpression>>)

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("should.be.replaced.with.list.generator")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val calleeText = element.calleeExpression?.text ?: return false
        val mapIndexedFqName = StandardKotlinNames.Collections.mapIndexed
        if (calleeText != mapIndexedFqName.shortName().asString() && element.containingKtFile.importDirectives.none {
                it.importedFqName == mapIndexedFqName && calleeText == it.aliasName
            }) return false
        val valueArgument = element.valueArguments.singleOrNull() ?: element.lambdaArguments.singleOrNull() ?: return false
        val valueParameters = when (val argumentExpression = valueArgument.getLambdaOrNamedFunction()) {
            is KtLambdaExpression -> argumentExpression.valueParameters
            is KtNamedFunction -> argumentExpression.valueParameters
            else -> return false
        }
        return valueParameters.size == 2
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.symbol as? KaNamedFunctionSymbol ?: return null
        if (symbol.importableFqName != StandardKotlinNames.Collections.mapIndexed) return null
        val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol
        val receiver = partiallyAppliedSymbol.dispatchReceiver ?: partiallyAppliedSymbol.extensionReceiver ?: return null

        if (!receiver.type.isSubtypeOf(StandardClassIds.Collection)) return null
        val valueArgument = element.valueArguments.singleOrNull() ?: element.lambdaArguments.singleOrNull() ?: return null
        val returns = mutableListOf<SmartPsiElementPointer<KtReturnExpression>>()
        val valueParameters = when (val argumentExpression = valueArgument.getLambdaOrNamedFunction()) {
            is KtLambdaExpression -> {
                val functionLiteral = argumentExpression.functionLiteral
                functionLiteral.collectDescendantsOfType<KtReturnExpression>().forEach {
                    val targetsMapIndexed = it.getTargetLabel()
                        ?.mainReference
                        ?.resolveToSymbol()
                        ?.psi == functionLiteral

                    if (targetsMapIndexed) {
                        returns.add(it.createSmartPointer())
                    }
                }
                argumentExpression.valueParameters
            }

            is KtNamedFunction -> argumentExpression.valueParameters
            else -> return null
        }
        if (valueParameters.size != 2) return null
        val secondParameter = valueParameters[1]
        val destructuringDeclaration = secondParameter.destructuringDeclaration
        if (destructuringDeclaration != null) {
            if (destructuringDeclaration.entries.any { entry -> entry.hasUsages(element) }) return null
        } else if (secondParameter.hasUsages(element)) {
            return null
        }

        return Context(returns)
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.list.generator.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val qualifiedExpression = element.getQualifiedExpressionForSelector()
            val receiverExpression = qualifiedExpression?.receiverExpression
            val valueArgument = element.valueArguments.singleOrNull() ?: element.lambdaArguments.singleOrNull() ?: return
            val writableReturnExpressions = context.labeledReturnExpressions.mapNotNull { updater.getWritable(it.element) }
            val psiFactory = KtPsiFactory(project)
            when (val argumentExpression = valueArgument.getLambdaOrNamedFunction()) {
                is KtLambdaExpression -> {
                    val functionLiteral = argumentExpression.functionLiteral
                    functionLiteral.valueParameterList?.replace(
                        psiFactory.createLambdaParameterList(argumentExpression.valueParameters.first().text)
                    )
                    writableReturnExpressions.forEach {
                        val returnedExpression = it.returnedExpression
                        if (returnedExpression != null) {
                            it.replace(psiFactory.createExpressionByPattern("return@List $0", returnedExpression))
                        } else {
                            it.replace(psiFactory.createExpressionByPattern("return@List"))
                        }
                    }
                    if (receiverExpression != null) {
                        qualifiedExpression.replace(
                            psiFactory.createExpressionByPattern("List($0.size) $1", receiverExpression, argumentExpression.text)
                        )
                    } else {
                        element.replace(psiFactory.createExpressionByPattern("List(size) ${argumentExpression.text}"))
                    }
                }

                is KtNamedFunction -> {
                    argumentExpression.valueParameterList?.replace(
                        psiFactory.createParameterList("(${argumentExpression.valueParameters.first().text})")
                    )
                    if (receiverExpression != null) {
                        qualifiedExpression.replace(
                            psiFactory.createExpressionByPattern("List($0.size, $1)", receiverExpression, argumentExpression.text)
                        )
                    } else {
                        element.replace(psiFactory.createExpressionByPattern("List(size, ${argumentExpression.text})"))
                    }
                }

                else -> return
            }
        }

    }
}

private fun KtValueArgument.getLambdaOrNamedFunction(): KtExpression? {
    return when (val argumentExpression = getArgumentExpression()) {
        is KtLambdaExpression, is KtNamedFunction -> argumentExpression
        is KtLabeledExpression -> argumentExpression.baseExpression as? KtLambdaExpression
        else -> null
    }
}
