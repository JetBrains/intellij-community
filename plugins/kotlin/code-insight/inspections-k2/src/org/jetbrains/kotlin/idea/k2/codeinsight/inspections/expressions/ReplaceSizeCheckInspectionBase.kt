// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal sealed class ReplaceSizeCheckInspectionBase :
  AbstractKotlinApplicableInspectionWithContext<KtBinaryExpression, ReplaceSizeCheckInspectionBase.ReplacementInfo>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    enum class EmptinessCheckMethod(val callString: String) {
        IS_EMPTY("isEmpty()"), IS_NOT_EMPTY("isNotEmpty()")
    }

    protected abstract val methodToReplaceWith: EmptinessCheckMethod

    protected abstract fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression?

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        return extractTargetExpressionFromPsi(element) != null
    }

    context(KtAnalysisSession)
    final override fun prepareContext(element: KtBinaryExpression): ReplacementInfo? {
        val target = extractTargetExpressionFromPsi(element) ?: return null
        return getReplacementIfApplicable(target)
    }

    override fun apply(element: KtBinaryExpression, context: ReplacementInfo, project: Project, updater: ModPsiUpdater) {
        val target = extractTargetExpressionFromPsi(element) as? KtDotQualifiedExpression
        val replacedCheck = KtPsiFactory(project).createExpression(context.expressionString(target))
        element.replace(replacedCheck)
    }

    data class ReplacementInfo(val method: EmptinessCheckMethod, val negate: Boolean) {

        fun expressionString(expr: KtDotQualifiedExpression?) = buildString {
            if (negate) append("!")
            if (expr != null) append("${expr.receiverExpression.text}.")
            append(method.callString)
        }

        fun fixMessage(): String = expressionString(null)
    }

    context(KtAnalysisSession)
    private fun getReplacementIfApplicable(target: KtExpression): ReplacementInfo? {
        val resolvedCall = target.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>() ?: return null
        val replaceableCall = resolvedCall.findReplaceableOverride() ?: return null

        val replaceWithNegatedIsEmpty = methodToReplaceWith == EmptinessCheckMethod.IS_NOT_EMPTY && !replaceableCall.hasIsNotEmpty
        return if (replaceWithNegatedIsEmpty) {
            // Some classes (like *Progression) don't have isNotEmpty() (KT-51560), so we use !isEmpty() instead
            ReplacementInfo(EmptinessCheckMethod.IS_EMPTY, negate = true)
        } else {
            ReplacementInfo(methodToReplaceWith, negate = false)
        }
    }

    context(KtAnalysisSession)
    private fun KtCallableMemberCall<*, *>.findReplaceableOverride(): ReplaceableCall? {
        val partiallyAppliedSymbol = this.partiallyAppliedSymbol
        val receiverType = (partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver)
            ?.type
            ?: return null

        val symbolWithOverrides = sequence {
            yield(partiallyAppliedSymbol.symbol)
            yieldAll(partiallyAppliedSymbol.symbol.getAllOverriddenSymbols())
        }
        val replaceableCall = symbolWithOverrides.firstNotNullOfOrNull { symbol ->
            when (this) {
                is KtVariableAccessCall -> REPLACEABLE_FIELDS_BY_CALLABLE_ID[symbol.callableIdIfNonLocal]

                is KtFunctionCall -> REPLACEABLE_COUNT_CALL.takeIf {
                    symbol.callableIdIfNonLocal == REPLACEABLE_COUNT_CALL.callableId && this.partiallyAppliedSymbol.signature.valueParameters.isEmpty()
                }
            }
        } ?: return null

        val receiverTypeAndSuperTypes = sequence {
            yield(receiverType)
            yieldAll(receiverType.getAllSuperTypes())
        }
        if (receiverTypeAndSuperTypes.any { it.expandedClassSymbol?.classIdIfNonLocal in replaceableCall.supportedReceivers }) {
            return replaceableCall
        } else {
            return null
        }
    }
}

/**
 * Description of a call that returns "size" of an object
 *
 * Stores data about size-like calls that can be converted to the isEmpty() or isNotEmpty() check.
 * In case the isNotEmpty() check for given receivers is not available, !isEmpty() will be used instead.
 *
 * @property callableId The [CallableId] of the field or method that can be replaced.
 * @property supportedReceivers The set of extension/dispatch receivers.
 * @property hasIsNotEmpty True if receivers have `isNotEmpty()` method. It is assumed that all receivers have an `isEmpty()` method.
 */
private data class ReplaceableCall(
    val callableId: CallableId,
    val supportedReceivers: Set<ClassId>,
    val hasIsNotEmpty: Boolean
)

private val COUNT_CALLABLE_ID = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("count"))
private val CHAR_SEQUENCE_CALLABLE_ID = ClassId.fromString("kotlin/CharSequence")

private val REPLACEABLE_FIELDS: Set<ReplaceableCall> = buildSet {
    add(
        ReplaceableCall(
            CallableId(CHAR_SEQUENCE_CALLABLE_ID, Name.identifier("length")),
            setOf(CHAR_SEQUENCE_CALLABLE_ID),
            hasIsNotEmpty = true
        )
    )
    val sizeFieldReceivers = buildSet {
        add(StandardClassIds.Collection)
        add(StandardClassIds.Array)
        add(StandardClassIds.Map)
        addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
        addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
    }
    for (classId in sizeFieldReceivers) {
        add(
            ReplaceableCall(
                CallableId(classId, Name.identifier("size")),
                setOf(classId),
                hasIsNotEmpty = true,
            )
        )
    }
}

private val REPLACEABLE_FIELDS_BY_CALLABLE_ID: Map<CallableId, ReplaceableCall> =
    REPLACEABLE_FIELDS.associateBy { call -> call.callableId }

private val REPLACEABLE_COUNT_CALL = ReplaceableCall(
    COUNT_CALLABLE_ID,
    buildSet {
        val progressionBasePrimitiveTypes = listOf(
            StandardClassIds.Char,
            StandardClassIds.Int,
            StandardClassIds.UInt,
            StandardClassIds.Long,
            StandardClassIds.ULong
        )
        for (primitiveClassId in progressionBasePrimitiveTypes) {
            add(ClassId(StandardClassIds.BASE_RANGES_PACKAGE, Name.identifier("${primitiveClassId.shortClassName}Progression")))
        }
    },
    hasIsNotEmpty = false
)
