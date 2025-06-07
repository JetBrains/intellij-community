/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

/**
 * The inspection detects calls to Kotlin standard library functions `filterIsInstance`/`filterIsInstanceTo`,
 * whose target type is not a subtype of the element type the filtering is called on.
 *
 * The inspection doesn't provide any quick fix.
 * That's because the fix shouldn't break working code, however, it's hard to detect whether the fix is breaking something or not.
 * `filterIsInstance` calls affect the type of the resulting collection, so deleting the call is not an option.
 * The following example shows valid code that would become invalid after deleting the `filterIsInstance` call.
 * ```kotlin
 * val listOfInts = listOf<String>("Hello").filterIsInstance<Int>()
 * val sum: Int = listOfInts.sum()
 * ```
 */
class FilterIsInstanceResultIsAlwaysEmptyInspection: AbstractKotlinInspection() {
    private val filterIsInstanceCallableIds = listOf<CallableId>(
        CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("filterIsInstance")),
        CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("filterIsInstanceTo")),
        CallableId(StandardClassIds.BASE_SEQUENCES_PACKAGE, Name.identifier("filterIsInstance")),
        CallableId(StandardClassIds.BASE_SEQUENCES_PACKAGE, Name.identifier("filterIsInstanceTo")),
    )

    private val filterIsInstanceShortNames: Set<String> = filterIsInstanceCallableIds.map { it.callableName.asString() }.toSet()

    /**
     * The following is the proof of the inspection's correctness:
     *
     * Let's introduce three types:
     *      S (source) - element type of the collection the filtering is performed on
     *      T (target) - filtering target type
     *      R (result) - type of elements that remain in the collection after the filtering is applied
     *
     * Given some collection `Collection<S>`,
     * we want to avoid it that a call `collection.filterIsInstance<T>()`
     * is marked as an error even though it’s actually correct and does not produce an empty collection.
     * So we have to find an element which can both be in the collection and remain in the collection after the filtering.
     *
     * Such an element has a type R, which is a subtype of both S (can be in the collection) and T (remains in the collection).
     * Given that the [filterIsInstance] call is marked as an error, we can assume that T is NOT a subtype of S and vice versa.
     *
     *
     * The first case to consider is when both T and S to be class types (with any modality):
     * Then we can say that R’s superclass must be S (directly or indirectly),
     * as otherwise it couldn’t be included in the collection.
     * But then R cannot also be a subtype of T:
     * 1. Class inheritance is linear, so R cannot have two superclasses.
     * If S is already a direct or indirect superclass,
     * the only option for T to still be a superclass of R would be if it’s also a subclass or superclass of S.
     * 2. But we’ve already established T </: S and S </: T, so T can neither be a superclass nor subclass of S.
     * Hence, R cannot be a subtype of T, and thus the [filterIsInstance] call will produce an empty collection.
     *
     *
     *
     * The second case is when S is a concrete type with `final` modality
     * while T is bounded by at least one type (and vice versa):
     * Let S be `final`.
     * Then R obviously has to be equal to S (as R <: S by construction).
     * For the resulting collection to be non-empty, S = R <: T also should be satisfied.
     * That means that S should be a subtype of all T's upperbounds:
     * S <: T <: upperbounds(T)
     * Or otherwise T is not a supertype to S, so the resulting collection is empty.
     *
     * The same logic can be applied to the case when T is `final` instead.
     */
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return callExpressionVisitor { ktCall ->
            analyze(ktCall) {
                val callExpression = ktCall.getPossiblyQualifiedCallExpression() ?: return@callExpressionVisitor
                val calleeExpression = callExpression.calleeExpression ?: return@callExpressionVisitor
                if (calleeExpression.text !in filterIsInstanceShortNames) return@callExpressionVisitor

                val callSymbol =
                    calleeExpression.resolveToCall()?.successfulCallOrNull<KaSimpleFunctionCall>()?.partiallyAppliedSymbol
                        ?: return@callExpressionVisitor

                val callableId = callSymbol.symbol.callableId ?: return@callExpressionVisitor
                val callableName = callableId.callableName
                if (callableId !in filterIsInstanceCallableIds) return@callExpressionVisitor

                val receiverType = callSymbol.extensionReceiver?.type as? KaClassType ?: return@callExpressionVisitor
                val receiverElementType = receiverType.typeArguments.singleOrNull()?.type ?: return@callExpressionVisitor

                val targetType =
                    (callSymbol.signature.returnType as? KaClassType)?.typeArguments?.singleOrNull()?.type ?: return@callExpressionVisitor

                val targetTypeBounds = targetType.boundsOrSelf ?: return@callExpressionVisitor
                val elementTypeBounds = receiverElementType.boundsOrSelf ?: return@callExpressionVisitor

                val targetTypeClassBound =
                    targetTypeBounds.singleOrNull { (it.symbol as? KaNamedClassSymbol)?.classKind != KaClassKind.INTERFACE }
                val elementTypeClassBound =
                    elementTypeBounds.singleOrNull { (it.symbol as? KaNamedClassSymbol)?.classKind != KaClassKind.INTERFACE }

                if (targetTypeClassBound?.isFinal == true && elementTypeBounds.any { !targetTypeClassBound.isSubtypeOf(it) }) {
                    holder.registerProblem(
                        ktCall,
                        KotlinBundle.message("inspection.message.result.of.0.always.empty.collection", callableName),
                    )
                    return@callExpressionVisitor
                }
                if (elementTypeClassBound?.isFinal == true && targetTypeBounds.any { !elementTypeClassBound.isSubtypeOf(it) }) {
                    holder.registerProblem(
                        ktCall,
                        KotlinBundle.message("inspection.message.result.of.0.always.empty.collection", callableName),
                    )
                    return@callExpressionVisitor
                }

                if (elementTypeClassBound == null || targetTypeClassBound == null) return@callExpressionVisitor

                if (!targetTypeClassBound.isSubtypeOf(elementTypeClassBound) &&
                    !elementTypeClassBound.isSubtypeOf(targetTypeClassBound)
                ) {
                    holder.registerProblem(
                        ktCall,
                        KotlinBundle.message("inspection.message.result.of.0.always.empty.collection", callableName),
                    )
                }
            }
        }
    }

    private val KaType.boundsOrSelf: List<KaType>?
        get() = when(this) {
            is KaTypeParameterType -> this.symbol.upperBounds
            is KaClassType -> listOf(this)
            else -> null
        }

    private val KaType.isFinal: Boolean
        get() = this.symbol?.modality == KaSymbolModality.FINAL
}