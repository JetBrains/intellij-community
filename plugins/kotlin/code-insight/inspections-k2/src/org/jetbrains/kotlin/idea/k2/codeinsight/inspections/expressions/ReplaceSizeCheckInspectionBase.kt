// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal sealed class ReplaceSizeCheckInspectionBase :
    KotlinApplicableInspectionBase.Simple<KtBinaryExpression, ReplaceSizeCheckInspectionBase.ReplacementInfo>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = binaryExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    enum class EmptinessCheckMethod(val methodName: String) {
        IS_EMPTY("isEmpty"),
        IS_NOT_EMPTY("isNotEmpty"),
    }

    protected abstract val methodToReplaceWith: EmptinessCheckMethod

    protected abstract fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression?

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.getStrictParentOfType<KtFunction>()
            ?.takeIf { it.valueParameters.isEmpty() }
            ?.name != methodToReplaceWith.methodName
                && extractTargetExpressionFromPsi(element) != null

    final override fun KaSession.prepareContext(element: KtBinaryExpression): ReplacementInfo? {
        val replaceableCall = extractTargetExpressionFromPsi(element)
            ?.takeUnless { it is KtSafeQualifiedExpression }
            ?.resolveToCall()
            ?.singleCallOrNull<KaCallableMemberCall<*, *>>()
            ?.findReplaceableOverride()
            ?: return null

        val replaceWithNegatedIsEmpty = methodToReplaceWith == EmptinessCheckMethod.IS_NOT_EMPTY
                && !replaceableCall.hasIsNotEmpty

        return if (replaceWithNegatedIsEmpty) {
            // Some classes (like *Progression) don't have isNotEmpty() (KT-51560), so we use !isEmpty() instead
            ReplacementInfo(EmptinessCheckMethod.IS_EMPTY, negate = true)
        } else {
            ReplacementInfo(methodToReplaceWith, negate = false)
        }
    }

    protected abstract inner class ReplaceSizeCheckQuickFixBase(
        private val context: ReplacementInfo,
    ) : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            val receiverExpression = (extractTargetExpressionFromPsi(element) as? KtDotQualifiedExpression)
                ?.receiverExpression

            val replacedCheck = KtPsiFactory(project)
                .createExpression(expressionString(context, receiverExpression?.text))
            element.replace(replacedCheck)
        }

        override fun getName(): @IntentionName String =
            KotlinBundle.message("replace.size.check.with.0", expressionString(context))
    }

    data class ReplacementInfo(
        val method: EmptinessCheckMethod,
        val negate: Boolean,
    )

    private fun expressionString(
        info: ReplacementInfo,
        receiverText: @NlsSafe String? = null,
    ): @NlsSafe String = buildString {
        if (info.negate) append("!")
        if (receiverText != null) {
            append(receiverText)
            append(".")
        }
        append(info.method.methodName)
        append("()")
    }

    context(_: KaSession)
    private fun KaCallableMemberCall<*, *>.findReplaceableOverride(): ReplaceableCall? {
        val partiallyAppliedSymbol = this.partiallyAppliedSymbol
        val receiverType = (partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver)
            ?.type
            ?: return null

        val symbolWithOverrides = partiallyAppliedSymbol.symbol.allOverriddenSymbolsWithSelf

        val replaceableCall = symbolWithOverrides.firstNotNullOfOrNull { symbol ->
            when (this) {
                is KaVariableAccessCall -> REPLACEABLE_FIELDS_BY_CALLABLE_ID[symbol.callableId]

                is KaFunctionCall -> REPLACEABLE_COUNT_CALL.takeIf {
                    symbol.callableId == REPLACEABLE_COUNT_CALL.callableId && this.partiallyAppliedSymbol.signature.valueParameters.isEmpty()
                }
            }
        } ?: return null

        val receiverTypeAndSuperTypes = sequence {
            yield(receiverType)
            yieldAll(receiverType.allSupertypes)
        }
        if (receiverTypeAndSuperTypes.any { it.expandedSymbol?.classId in replaceableCall.supportedReceivers }) {
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
    val hasIsNotEmpty: Boolean,
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
