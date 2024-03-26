// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.base.psi.expressionComparedToNull
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatement
import org.jetbrains.kotlin.idea.base.psi.prependDotQualifiedReceiver
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpressionOrThis
import org.jetbrains.kotlin.idea.codeinsights.impl.base.insertSafeCallsAfterReceiver
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.replaceVariableCallsWithExplicitInvokeCalls
import org.jetbrains.kotlin.idea.codeinsights.impl.base.wrapWithLet
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

@ApiStatus.Internal
data class IfThenTransformationData(
    val ifExpression: KtIfExpression,
    val condition: KtOperationExpression,
    /**
     * Expression checked in [condition].
     */
    val checkedExpression: KtExpression,
    val baseClause: KtExpression,
    val negatedClause: KtExpression?,
)

@ApiStatus.Internal
enum class TransformIfThenReceiverMode {
    ADD_EXPLICIT_THIS,

    /**
     * Indicates that the base clause and checked expression are the same.
     */
    REPLACE_BASE_CLAUSE,

    /**
     * Indicates that checked expression is a receiver in the base clause, and we need to find it and then replace it.
     */
    FIND_AND_REPLACE_MATCHING_RECEIVER,
}

@ApiStatus.Internal
object IfThenTransformationUtils {
    @RequiresWriteLock
    fun transformBaseClause(data: IfThenTransformationData, strategy: IfThenTransformationStrategy): KtExpression {
        val factory = KtPsiFactory(data.baseClause.project)

        val newReceiverExpression = when (val condition = data.condition) {
            is KtIsExpression -> {
                val typeReference = condition.typeReference
                    ?: errorWithAttachment("Null type reference in condition") { withPsiEntry("ifExpression", data.ifExpression) }

                factory.createExpressionByPattern("$0 as? $1", condition.leftHandSide, typeReference)
            }

            else -> data.checkedExpression
        }

        return when (strategy) {
            is IfThenTransformationStrategy.WrapWithLet -> data.baseClause.wrapWithLet(
                newReceiverExpression,
                expressionsToReplaceWithLambdaParameter = collectTextBasedUsages(data)
            )

            is IfThenTransformationStrategy.AddSafeAccess -> {
                // step 1. replace variable calls with explicit invoke calls
                val newBaseClause = data.baseClause.getLeftMostReceiverExpressionOrThis()
                    // TODO: use `OperatorToFunctionConverter.convert` instead
                    .replaceVariableCallsWithExplicitInvokeCalls(strategy.variableCallsToAddInvokeTo)

                // step 2. add an explicit receiver or replace the existing one
                val replacedReceiver = when (strategy.transformReceiverMode) {
                    TransformIfThenReceiverMode.ADD_EXPLICIT_THIS -> {
                        val leftMostReceiver = newBaseClause.getLeftMostReceiverExpressionOrThis()
                        val qualified = leftMostReceiver.prependDotQualifiedReceiver(newReceiverExpression, factory)

                        (qualified as KtQualifiedExpression).receiverExpression
                    }

                    TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE -> newBaseClause.replaced(newReceiverExpression)

                    TransformIfThenReceiverMode.FIND_AND_REPLACE_MATCHING_RECEIVER -> {
                        val receiverToReplace = newBaseClause.getMatchingReceiver(data.checkedExpression.text) ?: error("")
                        receiverToReplace.replaced(newReceiverExpression)
                    }
                }

                // step 3. add safe access after replaced receiver
                replacedReceiver.insertSafeCallsAfterReceiver()
            }
        }
    }

    fun buildTransformationData(ifExpression: KtIfExpression): IfThenTransformationData? {
        val condition = ifExpression.condition?.getSingleUnwrappedStatement() as? KtOperationExpression ?: return null
        val thenClause = ifExpression.then?.let { it.getSingleUnwrappedStatement() ?: return null }
        val elseClause = ifExpression.`else`?.let { it.getSingleUnwrappedStatement() ?: return null }
        val receiverExpression = condition.checkedExpression()?.getSingleUnwrappedStatement() ?: return null

        val (baseClause, negatedClause) = when (condition) {
            is KtBinaryExpression -> when (condition.operationToken) {
                KtTokens.EQEQ -> elseClause to thenClause
                KtTokens.EXCLEQ -> thenClause to elseClause
                else -> return null
            }

            is KtIsExpression -> {
                when (condition.isNegated) {
                    true -> elseClause to thenClause
                    false -> thenClause to elseClause
                }
            }

            else -> return null
        }

        if (baseClause == null) return null

        return IfThenTransformationData(ifExpression, condition, receiverExpression, baseClause, negatedClause)
    }

    fun KtExpression.checkedExpression(): KtExpression? = when (this) {
        is KtBinaryExpression -> expressionComparedToNull()
        is KtIsExpression -> leftHandSide
        else -> null
    }

    /**
     * @return usages of [IfThenTransformationData.checkedExpression] based on its text and `KClass`, excluding usages from nested scopes
     */
    fun collectTextBasedUsages(data: IfThenTransformationData): List<KtExpression> = data.baseClause.collectDescendantsOfType<KtExpression>(
        canGoInside = { it !is KtBlockExpression },
        predicate = { it::class == data.checkedExpression::class && it.text == data.checkedExpression.text },
    )
}

@ApiStatus.Internal
sealed class IfThenTransformationStrategy {
    abstract fun withWritableData(updater: ModPsiUpdater): IfThenTransformationStrategy

    /**
     * Returns `true` if the transformation is expected to make code more Kotlin-idiomatic, and so it should be suggested.
     */
    abstract fun shouldSuggestTransformation(): Boolean

    data object WrapWithLet : IfThenTransformationStrategy() {
        override fun withWritableData(updater: ModPsiUpdater): WrapWithLet = WrapWithLet

        override fun shouldSuggestTransformation(): Boolean = false
    }

    data class AddSafeAccess(
        val variableCallsToAddInvokeTo: Set<KtCallExpression>,
        val transformReceiverMode: TransformIfThenReceiverMode,
        val newReceiverIsSafeCast: Boolean,
    ) : IfThenTransformationStrategy() {
        override fun withWritableData(updater: ModPsiUpdater): AddSafeAccess = this.copy(
            variableCallsToAddInvokeTo.map { updater.getWritable<KtCallExpression>(it) }.toSet()
        )

        override fun shouldSuggestTransformation(): Boolean {
            val newReceiverIsSafeCastInParentheses =
                newReceiverIsSafeCast && transformReceiverMode != TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE

            return variableCallsToAddInvokeTo.isEmpty() && !newReceiverIsSafeCastInParentheses
        }
    }

    companion object {
        context(KtAnalysisSession)
        fun create(data: IfThenTransformationData): IfThenTransformationStrategy? {
            val newReceiverIsSafeCast = data.condition is KtIsExpression

            return if (data.checkedExpression is KtThisExpression && IfThenTransformationUtils.collectTextBasedUsages(data).isEmpty()) {
                val leftMostReceiver = data.baseClause.getLeftMostReceiverExpressionOrThis()
                if (!leftMostReceiver.hasImplicitReceiverMatchingThisExpression(data.checkedExpression)) return null

                AddSafeAccess(leftMostReceiver.collectVariableCalls(), TransformIfThenReceiverMode.ADD_EXPLICIT_THIS, newReceiverIsSafeCast)
            } else {
                val receiverToReplace = data.baseClause.getMatchingReceiver(data.checkedExpression.text) ?: return WrapWithLet
                val variableCalls = receiverToReplace.collectVariableCalls()

                val transformReceiverMode = if (variableCalls.isEmpty() && data.baseClause.isSimplifiableTo(data.checkedExpression)) {
                    TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE
                } else TransformIfThenReceiverMode.FIND_AND_REPLACE_MATCHING_RECEIVER

                AddSafeAccess(variableCalls, transformReceiverMode, newReceiverIsSafeCast)
            }
        }

        context(KtAnalysisSession)
        private fun KtExpression.hasImplicitReceiverMatchingThisExpression(thisExpression: KtThisExpression): Boolean {
            val thisExpressionSymbol = thisExpression.instanceReference.mainReference.resolveToSymbol() ?: return false
            // we need to resolve callee instead of call, because in case of variable call, call is resolved to `invoke`
            val callableMemberCall = this.getCalleeExpressionIfAny()?.resolveCallableMemberCall() ?: return false

            return callableMemberCall.getImplicitReceivers().any { it.symbol == thisExpressionSymbol }
        }

        context(KtAnalysisSession)
        private fun KtExpression.resolveCallableMemberCall(): KtCallableMemberCall<*, *>? = this.resolveCall()?.successfulCallOrNull()

        context(KtAnalysisSession)
        private fun KtExpression.collectVariableCalls(): Set<KtCallExpression> = this
            .parentsOfType<KtExpression>(withSelf = true)
            .mapNotNull { it.getSelectorOrThis() as? KtCallExpression }
            .filter { it.resolveCall()?.singleCallOrNull<KtSimpleFunctionCall>()?.isImplicitInvoke == true }
            .toSet()
    }
}

/**
 * Note, that if [IfThenTransformationData.checkedExpression] is used in variable call, variable call will be returned, e.g., for:
 * ```
 * if (a is Function0<*>) {
 *     a().hashCode()
 * } else null
 * ```
 * `a()` will be returned.
 */
private fun KtExpression.getMatchingReceiver(targetText: String): KtExpression? {
    val leftMostReceiver = this.getLeftMostReceiverExpressionOrThis()

    return leftMostReceiver.parentsOfType<KtExpression>(withSelf = true).firstOrNull { parent ->
        val valueArgumentList = (parent.getSelectorOrThis() as? KtCallExpression)?.valueArgumentList
        parent.text.removeSuffix(valueArgumentList?.text.orEmpty()) == targetText
    }
}

private fun KtExpression.getSelectorOrThis(): KtExpression = (this as? KtQualifiedExpression)?.selectorExpression ?: this
