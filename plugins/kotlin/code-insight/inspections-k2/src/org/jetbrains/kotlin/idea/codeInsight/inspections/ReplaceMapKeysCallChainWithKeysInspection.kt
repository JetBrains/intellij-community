// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.singleStatementOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.plus
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainExpressions
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds.BASE_COLLECTIONS_PACKAGE
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

private const val MAP_FUNCTION_NAME = "map"
private const val TO_SET_FUNCTION_NAME = "toSet"
private const val KEY_PROPERTY_NAME = "key"
private const val KEYS_PROPERTY_NAME = "keys"

private val COLLECTIONS_TO_SET_FQ_NAME = BASE_COLLECTIONS_PACKAGE + TO_SET_FUNCTION_NAME
private val MAP_ENTRY_KEY_FQ_NAME = FqName("kotlin.collections.Map.Entry.key")

internal class ReplaceMapKeysCallChainWithKeysInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, Unit>(),
    CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> {
        return qualifiedExpressionVisitor { qualifiedExpression ->
            visitTargetElement(qualifiedExpression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) {
            CallChainExpressions.from(element)?.firstCalleeExpression
        }

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val callChainExpressions = CallChainExpressions.from(element) ?: return false
        if (callChainExpressions.firstCalleeExpression.text != MAP_FUNCTION_NAME) return false
        if (callChainExpressions.secondCalleeExpression.text != TO_SET_FUNCTION_NAME) return false

        val lambdaExpression = callChainExpressions.firstCallExpression.singleLambdaExpression() ?: return false
        return PsiTreeUtil.findChildOfType(lambdaExpression, PsiComment::class.java) == null
    }

    override fun KaSession.prepareContext(element: KtQualifiedExpression): Unit? {
        val callChainExpressions = CallChainExpressions.from(element) ?: return null

        val firstCall =
            callChainExpressions.firstCalleeExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (firstCall.symbol.callableId?.asSingleFqName() != StandardKotlinNames.Collections.map) return null
        if (!firstCall.isCalledOnMapExtensionReceiver) return null

        val secondCall =
            callChainExpressions.secondCalleeExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (secondCall.symbol.callableId?.asSingleFqName() != COLLECTIONS_TO_SET_FQ_NAME) return null

        val lambdaExpression =
            callChainExpressions.firstCallExpression.singleLambdaExpression() ?: return null
        return lambdaExpression.mapsMapEntryToKey().asUnit
    }

    override fun getProblemDescription(element: KtQualifiedExpression, context: Unit): @InspectionMessage String =
        KotlinBundle.message("map.keys.call.chain.can.be.simplified")

    override fun createQuickFix(
        element: KtQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtQualifiedExpression> = object : KotlinModCommandQuickFix<KtQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.0", KEYS_PROPERTY_NAME)

        override fun applyFix(project: Project, element: KtQualifiedExpression, updater: ModPsiUpdater) {
            val callChainExpressions = CallChainExpressions.from(element) ?: return
            val psiFactory = KtPsiFactory(project)
            val firstExpression = callChainExpressions.firstExpression
            val replacement = when (firstExpression) {
                is KtSafeQualifiedExpression -> psiFactory.createExpressionByPattern(
                    "$0?.$KEYS_PROPERTY_NAME",
                    firstExpression.receiverExpression,
                    reformat = false,
                )
                is KtQualifiedExpression -> psiFactory.createExpressionByPattern(
                    "$0.$KEYS_PROPERTY_NAME",
                    firstExpression.receiverExpression,
                    reformat = false,
                )
                else -> psiFactory.createExpression(KEYS_PROPERTY_NAME)
            }

            element.replace(replacement)
        }
    }
}

private fun KtCallExpression.singleLambdaExpression(): KtLambdaExpression? {
    val argument = valueArguments.singleOrNull() ?: return null
    return (argument as? KtLambdaArgument)?.getLambdaExpression() ?: argument.getArgumentExpression() as? KtLambdaExpression
}

context(_: KaSession)
private fun KtLambdaExpression.mapsMapEntryToKey(): Boolean {
    val lambdaParameterSymbol = functionLiteral.symbol.valueParameters.singleOrNull() ?: return false
    val statement = singleStatementOrNull() ?: return false
    val returnedExpression = KtPsiUtil.safeDeparenthesize(statement) as? KtDotQualifiedExpression ?: return false

    val receiverExpression = returnedExpression.receiverExpression
    val receiverSymbol = receiverExpression.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return false
    if (receiverSymbol != lambdaParameterSymbol) return false

    val selectorExpression = returnedExpression.selectorExpression as? KtNameReferenceExpression ?: return false
    if (selectorExpression.getReferencedName() != KEY_PROPERTY_NAME) return false

    val keySymbol = selectorExpression.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return false
    return keySymbol.callableId?.asSingleFqName() == MAP_ENTRY_KEY_FQ_NAME
}
