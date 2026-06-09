// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatement
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isOneIntegerConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isZeroIntegerConstant
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.ifExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val IS_NOT_EMPTY_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("isNotEmpty")),
    CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isNotEmpty")),
)

private val IS_EMPTY_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("isEmpty")),
    CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isEmpty")),
)

private val COUNT_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("count")),
    CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("count")),
)

private val FIRST_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("first")),
    CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("first")),
)

private val SUPPORTED_ARRAY_CLASS_IDS: Set<ClassId> = buildSet {
    add(StandardClassIds.Array)
    addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
    addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
}

internal class ReplaceIfExpressionWithFirstOrNullInspection : KotlinApplicableInspectionBase.Simple<KtIfExpression, Unit>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = ifExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(element: KtIfExpression, context: Unit): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.if.expression.with.first.or.null.display.name")

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> = ApplicabilityRanges.ifKeyword(element)

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        val candidate = element.extractReplacementCandidate() ?: return false
        return candidate.receiver.isSimpleStableReceiver()
    }

    override fun KaSession.prepareContext(element: KtIfExpression): Unit? {
        val candidate = element.extractReplacementCandidate() ?: return null
        val receiver = candidate.firstElementRead.receiver

        if (!candidate.receiver.isSemanticMatch(receiver)) return null
        if (!isSupportedReceiverType(receiver)) return null

        return (candidate.condition.isResolvable() && candidate.firstElementRead.isResolvable()).asUnit
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.0", "firstOrNull()")

        override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
            val candidate = element.extractReplacementCandidate() ?: return
            val replacement = KtPsiFactory(project).createExpressionByPattern("$0.firstOrNull()", candidate.firstElementRead.receiver)
            val commentSaver = CommentSaver(element)
            element.replace(replacement)
            commentSaver.restore(replacement)
        }
    }
}

private fun KaSession.isSupportedReceiverType(receiver: KtExpression): Boolean {
    val type = receiver.expressionType ?: return false
    return type.isSubtypeOf(StandardClassIds.List) ||
            type.isSubtypeOf(StandardClassIds.CharSequence) ||
            type.isSubtypeOf(StandardClassIds.Set) ||
            (type as? KaClassType)?.classId in SUPPORTED_ARRAY_CLASS_IDS
}

context(_: KaSession)
private fun Condition.isResolvable(): Boolean = when (this) {
    is Condition.EmptinessCall -> expression.extractCallExpression()
        ?.calleeCallableId() in callableIds

    is Condition.SizeCheck -> kind == SizeCheckKind.SizeOrLength ||
            expression.callExpression?.calleeCallableId() in COUNT_CALLABLE_IDS
}

context(_: KaSession)
private fun FirstElementRead.isResolvable(): Boolean = when (this) {
    is FirstElementRead.IndexedAccess -> true
    is FirstElementRead.FirstCall -> expression.callExpression?.calleeCallableId() in FIRST_CALLABLE_IDS
}

context(_: KaSession)
private fun KtCallExpression.calleeCallableId(): CallableId? =
    resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId

private enum class Emptiness {
    EMPTY,
    NON_EMPTY,
}

private enum class SizeCheckKind {
    SizeOrLength,
    Count,
}

private sealed interface Condition {
    val emptiness: Emptiness

    data class EmptinessCall(
        val expression: KtExpression,
        override val emptiness: Emptiness,
    ) : Condition {
        val callableIds: Set<CallableId>
            get() = when (emptiness) {
                Emptiness.EMPTY -> IS_EMPTY_CALLABLE_IDS
                Emptiness.NON_EMPTY -> IS_NOT_EMPTY_CALLABLE_IDS
            }
    }

    data class SizeCheck(
        val expression: KtDotQualifiedExpression,
        val kind: SizeCheckKind,
        override val emptiness: Emptiness,
    ) : Condition
}

private data class ReplacementCandidate(
    val receiver: KtExpression,
    val condition: Condition,
    val firstElementRead: FirstElementRead,
)

private sealed interface FirstElementRead {
    val receiver: KtExpression

    data class IndexedAccess(override val receiver: KtExpression) : FirstElementRead

    data class FirstCall(
        val expression: KtDotQualifiedExpression,
        override val receiver: KtExpression,
    ) : FirstElementRead
}

private data class ReceiverCondition(
    val receiver: KtExpression,
    val condition: Condition,
)

private data class SizeLikeAccess(
    val expression: KtDotQualifiedExpression,
    val receiver: KtExpression,
    val kind: SizeCheckKind,
)

private fun KtIfExpression.extractReplacementCandidate(): ReplacementCandidate? {
    val thenBranch = then?.getSingleUnwrappedStatement() ?: return null
    val elseBranch = `else`?.getSingleUnwrappedStatement() ?: return null
    val receiverCondition = condition
        ?.getSingleUnwrappedStatementOrThis()
        ?.extractReceiverCondition()
        ?: return null

    val firstElementRead = when (receiverCondition.condition.emptiness) {
        Emptiness.EMPTY -> elseBranch.takeIf { thenBranch.isNullExpression() }
        Emptiness.NON_EMPTY -> thenBranch.takeIf { elseBranch.isNullExpression() }
    }?.extractFirstElementRead() ?: return null

    return ReplacementCandidate(
        receiver = receiverCondition.receiver,
        condition = receiverCondition.condition,
        firstElementRead = firstElementRead,
    )
}

private fun KtExpression.extractReceiverCondition(): ReceiverCondition? = when (val expression = safeDeparenthesize()) {
    is KtCallExpression -> expression.extractImplicitReceiverEmptinessCondition()
    is KtDotQualifiedExpression -> expression.extractQualifiedEmptinessCondition()
    is KtBinaryExpression -> expression.extractSizeCheckCondition()
    else -> null
}

private fun KtExpression.extractFirstElementRead(): FirstElementRead? = when (val expression = safeDeparenthesize()) {
    is KtArrayAccessExpression -> expression.extractZeroIndexRead()
    is KtDotQualifiedExpression -> expression.extractFirstElementCall()
    else -> null
}

private fun KtArrayAccessExpression.extractZeroIndexRead(): FirstElementRead.IndexedAccess? {
    val index = indexExpressions.singleOrNull() ?: return null
    val receiver = arrayExpression ?: return null
    return FirstElementRead.IndexedAccess(receiver).takeIf { index.isZeroIntegerConstant }
}

private fun KtDotQualifiedExpression.extractFirstElementCall(): FirstElementRead? {
    val call = callExpression ?: return null
    return when (call.calleeName()) {
        "get" -> {
            val index = call.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
            FirstElementRead.IndexedAccess(receiverExpression).takeIf { index.isZeroIntegerConstant }
        }

        "first" -> FirstElementRead.FirstCall(this, receiverExpression).takeIf { call.hasNoArguments() }
        else -> null
    }
}

private fun KtExpression.extractCallExpression(): KtCallExpression? = when (val expression = safeDeparenthesize()) {
    is KtCallExpression -> expression
    is KtDotQualifiedExpression -> expression.callExpression
    else -> null
}

private fun KtDotQualifiedExpression.extractQualifiedEmptinessCondition(): ReceiverCondition? {
    val call = callExpression ?: return null
    val emptiness = call.extractEmptiness() ?: return null
    return ReceiverCondition(receiverExpression, Condition.EmptinessCall(this, emptiness))
}

private fun KtCallExpression.extractImplicitReceiverEmptinessCondition(): ReceiverCondition? {
    val emptiness = extractEmptiness() ?: return null
    val receiver = extractImplicitThisReceiver() ?: return null
    return ReceiverCondition(receiver, Condition.EmptinessCall(this, emptiness))
}

private fun KtCallExpression.extractEmptiness(): Emptiness? {
    if (!hasNoArguments()) return null
    return when (calleeName()) {
        "isEmpty" -> Emptiness.EMPTY
        "isNotEmpty" -> Emptiness.NON_EMPTY
        else -> null
    }
}

private fun KtCallExpression.extractImplicitThisReceiver(): KtExpression? {
    val scopeFunctionCall = getStrictParentOfType<KtLambdaExpression>()
        ?.findImplicitThisScopeFunctionCall()
        ?: return null

    return when (scopeFunctionCall.calleeName()) {
        "with" -> scopeFunctionCall.valueArguments.firstOrNull()?.getArgumentExpression()
        "run", "apply" -> (scopeFunctionCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        else -> null
    }
}

private fun KtLambdaExpression.findImplicitThisScopeFunctionCall(): KtCallExpression? = when (val parent = parent) {
    is KtLambdaArgument -> parent.parent as? KtCallExpression
    is KtValueArgument -> parent.parent?.parent as? KtCallExpression
    else -> null
}

private fun KtBinaryExpression.extractSizeCheckCondition(): ReceiverCondition? {
    val (sizeLikeExpression, emptiness) = extractSizeLikeOperand() ?: return null
    val sizeLikeAccess = sizeLikeExpression?.extractSizeLikeAccess() ?: return null

    return ReceiverCondition(
        receiver = sizeLikeAccess.receiver,
        condition = Condition.SizeCheck(sizeLikeAccess.expression, sizeLikeAccess.kind, emptiness),
    )
}

private fun KtBinaryExpression.extractSizeLikeOperand(): Pair<KtExpression?, Emptiness>? {
    val left = left ?: return null
    val right = right ?: return null

    return when (operationToken) {
        KtTokens.EQEQ -> when {
            right.isZeroIntegerConstant -> left to Emptiness.EMPTY
            left.isZeroIntegerConstant -> right to Emptiness.EMPTY
            else -> null
        }

        KtTokens.EXCLEQ -> when {
            right.isZeroIntegerConstant -> left to Emptiness.NON_EMPTY
            left.isZeroIntegerConstant -> right to Emptiness.NON_EMPTY
            else -> null
        }

        KtTokens.GTEQ -> when {
            right.isOneIntegerConstant -> left to Emptiness.NON_EMPTY
            left.isZeroIntegerConstant -> right to Emptiness.EMPTY
            else -> null
        }

        KtTokens.GT -> when {
            right.isZeroIntegerConstant -> left to Emptiness.NON_EMPTY
            left.isOneIntegerConstant -> right to Emptiness.EMPTY
            else -> null
        }

        KtTokens.LTEQ -> when {
            right.isZeroIntegerConstant -> left to Emptiness.EMPTY
            left.isOneIntegerConstant -> right to Emptiness.NON_EMPTY
            else -> null
        }

        KtTokens.LT -> when {
            right.isOneIntegerConstant -> left to Emptiness.EMPTY
            left.isZeroIntegerConstant -> right to Emptiness.NON_EMPTY
            else -> null
        }

        else -> null
    }
}

private fun KtExpression.extractSizeLikeAccess(): SizeLikeAccess? {
    val qualifiedExpression = safeDeparenthesize() as? KtDotQualifiedExpression ?: return null
    val receiver = qualifiedExpression.receiverExpression

    return when (val selector = qualifiedExpression.selectorExpression) {
        is KtNameReferenceExpression -> SizeLikeAccess(
            expression = qualifiedExpression,
            receiver = receiver,
            kind = SizeCheckKind.SizeOrLength,
        ).takeIf { selector.getReferencedName() in setOf("size", "length") }

        is KtCallExpression -> SizeLikeAccess(
            expression = qualifiedExpression,
            receiver = receiver,
            kind = SizeCheckKind.Count,
        ).takeIf { selector.calleeName() == "count" && selector.hasNoArguments() }

        else -> null
    }
}

private fun KtCallExpression.calleeName(): String? =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

private fun KtCallExpression.hasNoArguments(): Boolean =
    valueArguments.isEmpty() && lambdaArguments.isEmpty()

private fun KtExpression.isSimpleStableReceiver(): Boolean = when (val expression = safeDeparenthesize()) {
    is KtNameReferenceExpression, is KtThisExpression -> true
    is KtDotQualifiedExpression ->
        expression.selectorExpression is KtNameReferenceExpression && expression.receiverExpression.isSimpleStableReceiver()

    else -> false
}
