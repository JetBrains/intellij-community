// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

internal sealed class ReplaceSizeCheckInspectionBase : LocalInspectionTool() {

    protected enum class EmptinessCheckMethod(val callString: String) {
        IS_EMPTY("isEmpty()"),
        IS_NOT_EMPTY("isNotEmpty()")
    }

    protected abstract val methodToReplaceWith: EmptinessCheckMethod

    protected abstract fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression?

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        binaryExpressionVisitor { expr ->
            val target = extractTargetExpressionFromPsi(expr) ?: return@binaryExpressionVisitor
            val replacement = getReplacementIfApplicable(target) ?: return@binaryExpressionVisitor
            val message = KotlinBundle.message("replace.size.check.with.0", replacement.fixMessage())
            holder.registerProblem(expr, message, ReplaceSizeCheckFix(replacement))
        }

    private fun getReplacementIfApplicable(target: KtExpression): ReplacementInfo? =
        analyze(target) {
            val resolvedCall = target.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>() ?: return null
            val replaceableCall = findReplaceableOverride(resolvedCall) ?: return null

            val replaceWithNegatedIsEmpty = methodToReplaceWith == EmptinessCheckMethod.IS_NOT_EMPTY && !replaceableCall.hasIsNotEmpty
            val dotQualifiedSmartPointerOrNull = (target as? KtDotQualifiedExpression)?.createSmartPointer()
            return if (replaceWithNegatedIsEmpty) {
                // Some classes (like *Progression) don't have isNotEmpty() (KT-51560), we use !isEmpty() instead
                ReplacementInfo(dotQualifiedSmartPointerOrNull, EmptinessCheckMethod.IS_EMPTY, negate = true)
            } else {
                ReplacementInfo(dotQualifiedSmartPointerOrNull, methodToReplaceWith, negate = false)
            }
        }

    private fun KtAnalysisSession.findReplaceableOverride(call: KtCallableMemberCall<*, *>): ReplaceableCall? {
        val partiallyAppliedSymbol = call.partiallyAppliedSymbol
        val receiverType = (partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver)
            ?.type
            ?: return null

        val symbolWithOverrides = sequence {
            yield(partiallyAppliedSymbol.symbol)
            yieldAll(partiallyAppliedSymbol.symbol.getAllOverriddenSymbols())
        }
        val matchingReplacement = symbolWithOverrides.firstNotNullOfOrNull { symbol ->
            when (call) {
                is KtVariableAccessCall -> REPLACEABLE_FIELDS_BY_CALLABLE_ID[symbol.callableIdIfNonLocal]

                is KtFunctionCall -> REPLACEABLE_COUNT_CALL.takeIf {
                    symbol.callableIdIfNonLocal == REPLACEABLE_COUNT_CALL.callableId &&
                            call.partiallyAppliedSymbol.signature.valueParameters.isEmpty()
                }
            }
        } ?: return null

        val receiverTypeAndSuperTypes = sequence {
            yield(receiverType)
            yieldAll(receiverType.getAllSuperTypes())
        }
        if (receiverTypeAndSuperTypes.any { it.expandedClassSymbol?.classIdIfNonLocal in matchingReplacement.supportedReceivers }) {
            return matchingReplacement
        } else {
            return null
        }
    }

    @SafeTypeForPreview
    private data class ReplacementInfo(
        val target: SmartPsiElementPointer<KtDotQualifiedExpression>?,
        val method: EmptinessCheckMethod,
        val negate: Boolean
    ) {
        private fun messageWithExpression(expr: KtDotQualifiedExpression?) = buildString {
            if (negate) append("!")
            if (expr != null) append("${expr.receiverExpression.text}.")
            append(method.callString)
        }

        fun fixMessage(): String = messageWithExpression(null)
        fun expressionString(): String = messageWithExpression(target?.element)
    }


    private class ReplaceSizeCheckFix(val info: ReplacementInfo) : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("replace.size.check.with.0", info.fixMessage())

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expr = descriptor.psiElement
            if (info.target != null && info.target.element == null) {
                return
            }
            val replacedCheck = KtPsiFactory(project).createExpression(info.expressionString())
            expr.replace(replacedCheck)
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

internal fun KtExpression.isIntegerConstantOfValue(value: Int): Boolean {
    val deparenthesized = KtPsiUtil.deparenthesize(this) as? KtConstantExpression ?: return false
    return deparenthesized.elementType == KtStubElementTypes.INTEGER_CONSTANT && deparenthesized.text == value.toString()
}

// TODO: also support 0x0, 0b0, ...?
internal fun KtExpression.isZeroIntegerConstant() = isIntegerConstantOfValue(0)
internal fun KtExpression.isOneIntegerConstant() = isIntegerConstantOfValue(1)
