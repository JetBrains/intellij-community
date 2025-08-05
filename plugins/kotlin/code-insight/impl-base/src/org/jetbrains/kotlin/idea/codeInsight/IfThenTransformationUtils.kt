// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.diagnostics
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.smartCastInfo
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.base.psi.expressionComparedToNull
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatement
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.psi.prependDotQualifiedReceiver
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpressionOrThis
import org.jetbrains.kotlin.idea.codeinsight.utils.isImplicitInvokeCall
import org.jetbrains.kotlin.idea.codeinsights.impl.base.insertSafeCallsAfterReceiver
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.replaceVariableCallsWithExplicitInvokeCalls
import org.jetbrains.kotlin.idea.codeinsights.impl.base.wrapWithLet
import org.jetbrains.kotlin.idea.formatter.rightMarginOrDefault
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
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
                expressionsToReplaceWithLambdaParameter = collectCheckedExpressionUsages(data)
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
                        val receiverToReplace = newBaseClause.getMatchingReceiver(data.checkedExpression) ?: error("")
                        receiverToReplace.replaced(newReceiverExpression)
                    }
                }

                // step 3. add safe access after replaced receiver
                replacedReceiver.insertSafeCallsAfterReceiver()
            }
        }
    }

    context(_: KaSession)
    fun prepareIfThenTransformationStrategy(element: KtIfExpression, acceptUnstableSmartCasts: Boolean): IfThenTransformationStrategy? {
        val data = buildTransformationData(element) ?: return null

        if (data.negatedClause == null && data.baseClause.isUsedAsExpression) return null

        // every usage is expected to have smart cast info;
        // if smart cast is unstable, replacing usage with `it` can break code logic
        if (acceptUnstableSmartCasts) {
            if (data.negatedClause != null && !data.negatedClause.isNullExpression()) return null
        } else {
            if (collectCheckedExpressionUsages(data).any { it.doesNotHaveStableSmartCast() }) return null
        }

        if (conditionIsSenseless(data)) return null

        return IfThenTransformationStrategy.create(data)
    }

    context(_: KaSession)
    private fun KtExpression.doesNotHaveStableSmartCast(): Boolean {
        val expressionToCheck = when (this) {
            is KtThisExpression -> instanceReference
            else -> this
        }
        return expressionToCheck.smartCastInfo?.isStable != true
    }


    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun conditionIsSenseless(data: IfThenTransformationData): Boolean = data.condition
        .diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        .map { it.diagnosticClass }
        .any { it == KaFirDiagnostic.SenselessComparison::class || it == KaFirDiagnostic.UselessIsCheck::class }


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
     * @return usages of [IfThenTransformationData.checkedExpression]'s target
     */
    fun collectCheckedExpressionUsages(data: IfThenTransformationData): List<KtExpression> {
        val target = getReferenceTarget(data.checkedExpression).mainReference?.resolve() ?: return emptyList()
        return data.baseClause.collectDescendantsOfType<KtExpression>(
            canGoInside = { it !is KtBlockExpression },
            predicate = { it::class == data.checkedExpression::class && it.text == data.checkedExpression.text && getReferenceTarget(it).mainReference?.resolve() == target },
        )
    }


    context(session: KaSession)
    fun prepareIfThenToElvisInspectionData(element: KtIfExpression): IfThenToElvisInspectionData? {
        val transformationData = buildTransformationData(element) ?: return null
        val transformationStrategy = IfThenTransformationStrategy.create(transformationData) ?: return null

        if (element.expressionType?.isUnitType != false) return null
        if (!session.clausesReplaceableByElvis(transformationData)) return null
        val checkedExpressions = listOfNotNull(
            transformationData.checkedExpression,
            transformationData.baseClause,
            transformationData.negatedClause,
        )
        if (checkedExpressions.any { it.hasUnstableSmartCast() }) return null

        return IfThenToElvisInspectionData(
            isUsedAsExpression = element.isUsedAsExpression,
            transformationStrategy = transformationStrategy
        )
    }

    context(_: KaSession)
    private fun KtExpression.hasUnstableSmartCast(): Boolean {
        val expressionToCheck = when (this) {
            is KtThisExpression -> instanceReference
            else -> this
        }
        return expressionToCheck.smartCastInfo?.isStable == false
    }

    private fun KaSession.clausesReplaceableByElvis(data: IfThenTransformationData): Boolean =
        when {
            data.negatedClause == null || data.negatedClause.isNullOrBlockExpression() == true ->
                false
            (data.negatedClause as? KtThrowExpression)?.let { throwsNullPointerExceptionWithNoArguments(it) } == true ->
                false
            conditionHasIncompatibleTypes(data) ->
                false
            data.baseClause.isSimplifiableTo(data.checkedExpression) ->
                true
            data.baseClause.anyArgumentEvaluatesTo(data.checkedExpression) ->
                true
            hasImplicitReceiverReplaceableBySafeCall(data) || data.baseClause.hasFirstReceiverOf(data.checkedExpression) ->
                generateSequence(data.baseClause) { (it as? KtDotQualifiedExpression)?.receiverExpression }.toList()
                    .all { it.expressionType?.canBeNull == false || it.smartCastInfo?.smartCastType?.canBeNull == false }
            else ->
                false
        }
    private fun KtExpression.hasFirstReceiverOf(receiver: KtExpression): Boolean {
        val actualReceiver = (this as? KtDotQualifiedExpression)?.getLeftMostReceiverExpression() ?: return false
        return actualReceiver.isSimplifiableTo(receiver)
    }

    private val nullPointerExceptionNames = setOf(
        "kotlin.KotlinNullPointerException",
        "kotlin.NullPointerException",
        "java.lang.NullPointerException"
    )

    private fun KaSession.throwsNullPointerExceptionWithNoArguments(throwExpression: KtThrowExpression): Boolean {
        val thrownExpression = throwExpression.thrownExpression as? KtCallExpression ?: return false

        val nameExpression = thrownExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        val resolveCall = nameExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val declDescriptor = resolveCall.symbol.containingDeclaration ?: return false

        val exceptionName = (declDescriptor as? KaClassLikeSymbol)?.classId?.asSingleFqName()?.asString() ?: return false
        return exceptionName in nullPointerExceptionNames && thrownExpression.valueArguments.isEmpty()
    }

    private fun KtExpression.isNullOrBlockExpression(): Boolean {
        val innerExpression = this.getSingleUnwrappedStatementOrThis()
        return innerExpression is KtBlockExpression || innerExpression.node.elementType == KtNodeTypes.NULL
    }

    private fun KaSession.conditionHasIncompatibleTypes(data: IfThenTransformationData): Boolean {
        val isExpression = data.condition as? KtIsExpression ?: return false
        val targetType = isExpression.typeReference?.type ?: return true
        if (targetType.canBeNull) return true
        // TODO: the following check can be removed after fix of KT-14576
        val originalType = data.checkedExpression.expressionType ?: return true

        return !targetType.isSubtypeOf(originalType)
    }

    private fun KtExpression.anyArgumentEvaluatesTo(argument: KtExpression): Boolean {
        val callExpression = this as? KtCallExpression ?: return false
        val arguments = callExpression.valueArguments.map { it.getArgumentExpression() }
        return arguments.any { it?.isSimplifiableTo(argument) == true } && arguments.all { it is KtNameReferenceExpression }
    }

    private fun KaSession.hasImplicitReceiverReplaceableBySafeCall(data: IfThenTransformationData): Boolean =
        data.checkedExpression is KtThisExpression && getImplicitReceiverValue(data) != null

    private fun KaSession.getImplicitReceiverValue(data: IfThenTransformationData): KaReceiverValue? {
        val resolvedCall = data.baseClause.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return null
        return resolvedCall.getImplicitReceivers().firstOrNull()
    }
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
        context(_: KaSession)
        fun create(data: IfThenTransformationData): IfThenTransformationStrategy? {
            val newReceiverIsSafeCast = data.condition is KtIsExpression

            return if (data.checkedExpression is KtThisExpression && IfThenTransformationUtils.collectCheckedExpressionUsages(data).isEmpty()) {
                val leftMostReceiver = data.baseClause.getLeftMostReceiverExpressionOrThis()
                if (!leftMostReceiver.hasImplicitReceiverMatchingThisExpression(data.checkedExpression)) return null

                AddSafeAccess(leftMostReceiver.collectVariableCalls(), TransformIfThenReceiverMode.ADD_EXPLICIT_THIS, newReceiverIsSafeCast)
            } else {
                val receiverToReplace = data.baseClause.getMatchingReceiver(data.checkedExpression) ?: return WrapWithLet
                val variableCalls = receiverToReplace.collectVariableCalls()

                val transformReceiverMode = if (variableCalls.isEmpty() && data.baseClause.isSimplifiableTo(data.checkedExpression)) {
                    TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE
                } else TransformIfThenReceiverMode.FIND_AND_REPLACE_MATCHING_RECEIVER

                AddSafeAccess(variableCalls, transformReceiverMode, newReceiverIsSafeCast)
            }
        }

        context(_: KaSession)
        private fun KtExpression.hasImplicitReceiverMatchingThisExpression(thisExpression: KtThisExpression): Boolean {
            val thisExpressionSymbol = thisExpression.instanceReference.mainReference.resolveToSymbol() ?: return false
            // we need to resolve callee instead of call, because in case of variable call, call is resolved to `invoke`
            val callableMemberCall = this.getCalleeExpressionIfAny()?.resolveCallableMemberCall() ?: return false

            return callableMemberCall.getImplicitReceivers().any { it.symbol == thisExpressionSymbol }
        }

        context(_: KaSession)
        private fun KtExpression.resolveCallableMemberCall(): KaCallableMemberCall<*, *>? = this.resolveToCall()?.successfulCallOrNull()

        context(_: KaSession)
        private fun KtExpression.collectVariableCalls(): Set<KtCallExpression> = this
            .parentsOfType<KtExpression>(withSelf = true)
            .mapNotNull { it.getSelectorOrThis() as? KtCallExpression }
            .filter { it.isImplicitInvokeCall() == true }
            .toSet()
    }
}

private fun getReferenceTarget(expression: KtExpression): KtExpression = (expression as? KtThisExpression)?.instanceReference ?: expression.getSelectorOrThis()


/**
 * Note, that if [IfThenTransformationData.checkedExpression] is used in variable call, variable call will be returned, e.g., for:
 * ```
 * if (a is Function0<*>) {
 *     a().hashCode()
 * } else null
 * ```
 * `a()` will be returned.
 */
private fun KtExpression.getMatchingReceiver(targetExpr: KtExpression): KtExpression? {
    val target = getReferenceTarget(targetExpr).mainReference?.resolve() ?: return null
    val leftMostReceiver = this.getLeftMostReceiverExpressionOrThis()

    if ((leftMostReceiver as? KtCallExpression)?.calleeExpression?.mainReference?.resolve() == target) return leftMostReceiver

    return leftMostReceiver.parentsOfType<KtExpression>(withSelf = true).firstOrNull { parent ->
        val resolve = getReferenceTarget(parent).mainReference?.resolve()
        resolve == target
    }
}

private fun KtExpression.getSelectorOrThis(): KtExpression = (this as? KtQualifiedExpression)?.selectorExpression ?: this

class IfThenToSafeAccessFix(private val context: IfThenTransformationStrategy) : KotlinModCommandQuickFix<KtIfExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("simplify.foldable.if.then")

    override fun getName(): @IntentionName String {
        val transformReceiverMode = (context as? IfThenTransformationStrategy.AddSafeAccess)?.transformReceiverMode

        return if (transformReceiverMode == TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE) {
            if (context.newReceiverIsSafeCast) {
                KotlinBundle.message("replace.if.expression.with.safe.cast.expression")
            } else {
                KotlinBundle.message("remove.redundant.if.expression")
            }
        } else {
            KotlinBundle.message("replace.if.expression.with.safe.access.expression")
        }
    }

    override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
        val data = IfThenTransformationUtils.buildTransformationData(element) as IfThenTransformationData
        val transformedBaseClause = IfThenTransformationUtils.transformBaseClause(data, context.withWritableData(updater))

        element.replace(transformedBaseClause)
    }

    fun asModCommandAction(element: KtIfExpression): ModCommandAction = object : PsiUpdateModCommandAction<KtIfExpression>(element) {
        override fun invoke(
            context: ActionContext,
            element: KtIfExpression,
            updater: ModPsiUpdater
        ) {
            applyFix(element.project, element, updater)
        }

        override fun getPresentation(
            context: ActionContext,
            element: KtIfExpression
        ): Presentation {
            return Presentation.of(name)
        }

        override fun getFamilyName(): @IntentionFamilyName String = this@IfThenToSafeAccessFix.familyName
    }
}

class IfThenToElvisInspectionData(
    val isUsedAsExpression: Boolean,
    val transformationStrategy: IfThenTransformationStrategy
)

class IfThenToElviFix(private val context: IfThenToElvisInspectionData) : KotlinModCommandQuickFix<KtIfExpression>() {
    private fun elvisPattern(newLine: Boolean): String = if (newLine) "$0\n?: $1" else "$0 ?: $1"

    override fun getFamilyName(): String = KotlinBundle.message("replace.if.expression.with.elvis.expression")
    override fun applyFix(
        project: Project,
        element: KtIfExpression,
        updater: ModPsiUpdater
    ) {
        val transformationData = IfThenTransformationUtils.buildTransformationData(element) as IfThenTransformationData
        val negatedClause = transformationData.negatedClause ?: return
        val psiFactory = KtPsiFactory(element.project)
        val commentSaver = CommentSaver(element, saveLineBreaks = false)
        val margin = element.containingKtFile.rightMarginOrDefault
        val replacedBaseClause = IfThenTransformationUtils.transformBaseClause(
            data = transformationData,
            strategy = context.transformationStrategy.withWritableData(updater)
        )
        val newExpr = element.replaced(
            psiFactory.createExpressionByPattern(
                elvisPattern(replacedBaseClause.textLength + negatedClause.textLength + 5 >= margin),
                replacedBaseClause,
                negatedClause
            )
        )

        (KtPsiUtil.deparenthesize(newExpr) as KtBinaryExpression).also {
            commentSaver.restore(it)
        }
    }

    fun asModCommandAction(element: KtIfExpression) = object : PsiUpdateModCommandAction<KtIfExpression>(element) {
        override fun invoke(
            context: ActionContext,
            element: KtIfExpression,
            updater: ModPsiUpdater
        ) {
            applyFix(element.project, element, updater)
        }

        override fun getFamilyName(): @IntentionFamilyName String = this@IfThenToElviFix.familyName
    }
}
