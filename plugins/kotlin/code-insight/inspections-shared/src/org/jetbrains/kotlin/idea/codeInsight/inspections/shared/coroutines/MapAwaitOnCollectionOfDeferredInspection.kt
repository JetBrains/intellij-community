// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.singleStatementOrNull
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath

internal class MapAwaitOnCollectionOfDeferredInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.map.await.on.collection.of.deferred.description")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor { callExpression ->
        visitTargetElement(callExpression, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val lambdaArgument = element.singleLambdaArgumentExpression() ?: return null

        if (!isIterableMapFunctionCall(element)) return null
        if (!isLambdaWithSingleAwaitCallOnSingleParameter(lambdaArgument)) return null

        return Unit
    }

    private fun KaSession.isIterableMapFunctionCall(element: KtCallExpression): Boolean {
        val functionCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val actualReceiverType = functionCall.partiallyAppliedSymbol.extensionReceiver?.type ?: return false

        return isIterableMapFunction(functionCall.symbol) &&
                actualReceiverType.isSubtypeOf(StandardClassIds.Collection)
    }

    private fun KaSession.isIterableMapFunction(symbol: KaFunctionSymbol): Boolean {
        return symbol.callableId == KOTLIN_COLLECTIONS_MAP_ID &&
                symbol.receiverParameter?.returnType?.isSubtypeOf(StandardClassIds.Iterable) == true
    }
    private fun KaSession.isLambdaWithSingleAwaitCallOnSingleParameter(lambdaExpression: KtLambdaExpression): Boolean {
        val singleLambdaParameterSymbol = lambdaExpression.functionLiteral.symbol.valueParameters.singleOrNull() ?: return false
        val singleReturnedExpression = singleReturnedExpression(lambdaExpression) as? KtDotQualifiedExpression ?: return false

        val awaitCall = singleReturnedExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false

        val explicitAwaitReceiverValue = awaitCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue ?: return false
        val explicitReceiverAccessCall = explicitAwaitReceiverValue.expression.resolveToCall()?.successfulVariableAccessCall() ?: return false

        return awaitCall.symbol.callableId == KOTLINX_COROUTINES_DEFERRED_AWAIT_ID &&
                explicitReceiverAccessCall.symbol == singleLambdaParameterSymbol
    }

    private fun KaSession.singleReturnedExpression(lambdaExpression: KtLambdaExpression): KtExpression? {
        val singleStatement = lambdaExpression.singleStatementOrNull() ?: return null

        return when (singleStatement) {
            is KtReturnExpression if (singleStatement.targetSymbol == lambdaExpression.functionLiteral.symbol) -> singleStatement.returnedExpression
            else -> singleStatement
        }
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtCallExpression> {
        return object : KotlinModCommandQuickFix<KtCallExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String {
                return KotlinBundle.message("inspection.map.await.on.collection.of.deferred.replace.with.awaitAll")
            }

            override fun applyFix(
                project: Project,
                element: KtCallExpression,
                updater: ModPsiUpdater
            ) {
                // TODO Use Import insertion API after KTIJ-28838 is fixed
                val alreadyImportedByStarImport =
                    element.containingKtFile.importDirectives.any {
                        it.importPath == ImportPath(KOTLINX_COROUTINES_PACKAGE, isAllUnder = true)
                    }

                if (!alreadyImportedByStarImport) {
                    element.containingKtFile.addImport(KOTLINX_COROUTINES_AWAIT_ALL_ID.asSingleFqName())
                }

                element.replace(KtPsiFactory(project).createExpression("awaitAll()"))
            }
        }
    }
}

private val KOTLIN_COLLECTIONS_MAP_ID: CallableId = CallableId(FqName("kotlin.collections"), Name.identifier("map"))

private val KOTLINX_COROUTINES_PACKAGE: FqName = FqName("kotlinx.coroutines")

private val KOTLINX_COROUTINES_DEFERRED_AWAIT_ID = CallableId(ClassId(KOTLINX_COROUTINES_PACKAGE, Name.identifier("Deferred")), Name.identifier("await"))

private val KOTLINX_COROUTINES_AWAIT_ALL_ID = CallableId(KOTLINX_COROUTINES_PACKAGE, Name.identifier("awaitAll"))