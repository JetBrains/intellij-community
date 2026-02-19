// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.hasOrOverridesCallableId
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutineContextJobStatus.Companion.detectFor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression

internal sealed class CoroutineContextJobStatus {

    data object Unknown : CoroutineContextJobStatus()
    data object NoJob : CoroutineContextJobStatus()

    /**
     * Represents a context containing a [kotlinx.coroutines.Job].
     * 
     * Important: The information in this class reflects the most obvious source of a `Job` in the analyzed expression,
     * but does not necessarily represent the **actual** state of the resulting context. 
     * This is because other parts of the context (like unknown `CoroutineContext` parameters) may also contain a `Job`,
     * which could override or affect the values reported here.
     *
     * Example:
     * ```kt
     * fun CoroutineScope.usage(randomContext: CoroutineContext) {
     *   launch(Job() + randomContext) { ... }
     * }
     * ```
     * 
     * When analyzing `Job() + randomContext` expression by [detectFor], the resulting status 
     * will be a [WithJob] with [source] pointing to `Job()` expression, and [isCancellable] set to `false`,
     * even though the `randomContext` may contain 
     * a different [kotlinx.coroutines.Job] element (`NonCancellable`, for example).
     */
    data class WithJob(
        /**
         * The particular expression which introduces a [kotlinx.coroutines.Job] into the context.
         */
        val source: KtExpression,
        /**
         * The cancellability of the [kotlinx.coroutines.Job] present in the context.
         * 
         * The only [kotlinx.coroutines.Job] instance which is considered non-cancellable is exactly 
         * the [kotlinx.coroutines.NonCancellable] object.
         * 
         * It is important to track this property because [kotlinx.coroutines.NonCancellable] is not supposed
         * to be used with just any coroutine builder function.
         * 
         * See documentation on [kotlinx.coroutines.NonCancellable] for more details.
         */
        val isCancellable: Boolean,
    ) : CoroutineContextJobStatus()

    /**
     * Computes the [CoroutineContextJobStatus] after [other] context is being appended to this one with 
     * [kotlin.coroutines.CoroutineContext.plus] operation.
     * 
     * If both this and [other] statuses are [WithJob], then the rightmost [WithJob.source] is used as the resulting one.
     * 
     * See documentation on [WithJob] for the details about combining [WithJob] with [Unknown].
     */
    fun append(other: CoroutineContextJobStatus): CoroutineContextJobStatus {
        return when (other) {
            is Unknown -> this
            is NoJob -> this
            is WithJob -> other
        }
    }

    companion object {
        context(_: KaSession)
        fun detectFor(expression: KtExpression): CoroutineContextJobStatus {
            return expression.detectStatus()
        }

        context(_: KaSession)
        private fun KtExpression?.detectStatus(): CoroutineContextJobStatus {
            val expression = this ?: return Unknown
            
            if (expression is KtParenthesizedExpression) {
                return expression.expression.detectStatus()
            }

            val expressionType = expression.expressionType ?: return Unknown

            val resolvedCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()

            return when {
                expressionType.isSubtypeOf(CoroutinesIds.NonCancellable.ID) -> WithJob(expression, isCancellable = false)
                
                // We assume here that anything that has a `Job` type is a regular cancellable job. Technically, this is not always true,
                // since a random reference to a `Job` can actually point to a `NonCancellable` object instance.
                expressionType.isSubtypeOf(CoroutinesIds.Job.ID) -> WithJob(expression, isCancellable = true)

                expressionType.isSubtypeOf(CoroutinesIds.CoroutineDispatcher.ID) -> NoJob
                
                expressionType.isSubtypeOf(CoroutinesIds.Stdlib.CoroutineContext.ID) && 
                        !expressionType.isSubtypeOf(CoroutinesIds.Job.ID) && 
                        expressionType.symbol?.modality == KaSymbolModality.FINAL -> NoJob

                // TODO: Consider more precise detection for the custom `CoroutineContext`  and `CoroutineContext.Element` implementations
                
                resolvedCall?.symbol?.callableId == CoroutinesIds.currentCoroutineContext -> WithJob(expression, isCancellable = true)
                resolvedCall?.symbol?.callableId == CoroutinesIds.Stdlib.coroutineContext -> WithJob(expression, isCancellable = true)

                resolvedCall?.symbol?.hasOrOverridesCallableId(CoroutinesIds.CoroutineScope.coroutineContext) == true -> WithJob(expression, isCancellable = true)

                resolvedCall is KaFunctionCall && 
                        resolvedCall.symbol.hasOrOverridesCallableId(CoroutinesIds.Stdlib.CoroutineContext.plus) -> handleContextPlusCall(resolvedCall)

                resolvedCall is KaFunctionCall &&
                        resolvedCall.symbol.hasOrOverridesCallableId(CoroutinesIds.Stdlib.CoroutineContext.minusKey) -> handleContextMinusKeyCall(resolvedCall)

                else -> Unknown
            }
        }

        context(_: KaSession)
        private fun handleContextPlusCall(resolvedCall: KaFunctionCall<*>): CoroutineContextJobStatus {
            val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue

            val leftStatus = dispatchReceiver?.expression.detectStatus()
            val rightStatus = resolvedCall.argumentMapping.keys.singleOrNull().detectStatus()

            return leftStatus.append(rightStatus)
        }

        context(_: KaSession)
        private fun handleContextMinusKeyCall(resolvedCall: KaFunctionCall<*>): CoroutineContextJobStatus {
            val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue

            val originalStatus = dispatchReceiver?.expression.detectStatus()
            val keyToRemove = resolvedCall.argumentMapping.keys.singleOrNull()

            return if (keyToRemove?.expressionType?.isSubtypeOf(CoroutinesIds.Job.Key.ID) == true) {
                NoJob
            } else {
                originalStatus
            }
        }
    }
}