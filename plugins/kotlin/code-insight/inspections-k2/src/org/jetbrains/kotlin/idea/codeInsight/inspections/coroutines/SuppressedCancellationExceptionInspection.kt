// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines.SuppressedCancellationExceptionInspection.SuppressedCancellationExceptionInspectionData
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.idea.imports.addImportFor
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.topParenthesizedParentOrMe

internal class SuppressedCancellationExceptionInspection :
    KotlinApplicableInspectionBase<KtExpression, SuppressedCancellationExceptionInspectionData>() {

    sealed class SuppressedCancellationExceptionInspectionData(val element: PsiElement) {
        abstract val rangeInElement: TextRange?
        abstract val description: @InspectionMessage String
        abstract fun createFix(): PsiUpdateModCommandQuickFix

        // This is a var because we only set it at the very end of `prepareContext`.
        // This is done because building the scope context is expensive.
        var hasImplicitCoroutineScopeReceiver: Boolean = false
    }

    class RunCatchingDetection(call: KtCallExpression) : SuppressedCancellationExceptionInspectionData(call) {
        override val rangeInElement: TextRange? = call.calleeExpression?.textRangeInParent

        override val description: @InspectionMessage String
            get() = KotlinBundle.message("inspection.suppressed.cancellation.exception.run.catching.description")

        override fun createFix(): PsiUpdateModCommandQuickFix =
            AddEnsureActiveInRunCatchingQuickFix(hasImplicitCoroutineScopeReceiver)
    }

    class TryCatchDetection(private val tryExpression: KtTryExpression, private val catchClause: KtCatchClause) :
        SuppressedCancellationExceptionInspectionData(tryExpression) {
        override val rangeInElement: TextRange? = catchClause.parameterList?.textRangeInParent?.let {
            TextRange(0, it.endOffset).shiftRight(catchClause.startOffsetInParent)
        }

        override val description: @InspectionMessage String
            get() = KotlinBundle.message("inspection.suppressed.cancellation.exception.try.catch.description")

        override fun createFix(): PsiUpdateModCommandQuickFix = AddEnsureActiveToTryCatchQuickFix(
            catchClauseIndex = tryExpression.catchClauses.indexOf(catchClause),
            hasImplicitCoroutineScopeReceiver = hasImplicitCoroutineScopeReceiver
        )
    }

    private val RUN_CATCHING_BLOCK_PARAM = Name.identifier("block")

    // Parts of names indicating a callable does more with an exception than log it.
    private val HANDLING_NAME_PARTS: List<String> = listOf("handle", "throw")

    // Any of these callables does not handle the exception of a `Result`.
    private val definitelyDiscardingCallables = setOf(
        StandardKotlinNames.Result.isSuccess,
        StandardKotlinNames.Result.isFailure,
        StandardKotlinNames.Result.getOrNull,
        StandardKotlinNames.Result.getOrDefault,
    )

    // The callables in this list could handle the exception, but only if the lambdas passed to it do
    private val potentiallyHandlingCallables = setOf(
        StandardKotlinNames.Result.onFailure,
        StandardKotlinNames.Result.getOrElse,
        StandardKotlinNames.Result.recover,
        StandardKotlinNames.Result.recoverCatching,
        StandardKotlinNames.Result.fold,
    )

    // Any callable used on a `Result` not in this list, and the `potentiallyHandlingCallables` above could
    // suppress the cancellation exception, so we disable the inspection in these cases.
    private val nonExceptionHandlingCallables = setOf(
        StandardKotlinNames.Result.onSuccess,
        StandardKotlinNames.Result.map,
    )

    /**
     * A heuristic that decides whether the receiver with [this] name is
     * likely to be a logger.
     */
    private fun String.isLikelyLoggerReceiver(): Boolean {
        return startsWith("Log") ||
                contains("Logger") ||
                contains("Reporter") ||
                contains("Trace")
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: SuppressedCancellationExceptionInspectionData,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ context.element,
        /* rangeInElement = */ context.rangeInElement,
        /* descriptionTemplate = */ context.description,
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ context.createFix()
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) =
            visitTargetElement(expression, holder, isOnTheFly)

        override fun visitTryExpression(expression: KtTryExpression) =
            visitTargetElement(expression, holder, isOnTheFly)
    }

    /**
     * Finds all aliases for `runCatching` in the file, including the original name.
     * Using this is a significant performance improvement over `findImportByAlias` on the file.
     */
    private fun KtFile.runCatchingNamesWithAliases(): Set<Name> {
        val file = this
        return CachedValuesManager.getCachedValue(file) {
            val names = mutableSetOf(StandardKotlinNames.Result.runCatching.callableName)
            val runCatchingFqName = StandardKotlinNames.Result.runCatching.asSingleFqName()
            if (file.hasImportAlias()) {
                for (directive in file.importDirectives) {
                    val alias = directive.aliasName ?: continue
                    if (directive.importedFqName == runCatchingFqName) {
                        names += Name.identifier(alias)
                    }
                }
            }
            CachedValueProvider.Result.create<Set<Name>>(names, file)
        }
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        if (element.containingKtFile is KtCodeFragment) return false

        // This part checks for `runCatching`
        if (element is KtCallExpression) {
            val callName = (element.calleeExpression as? KtNameReferenceExpression)
                ?.getReferencedNameAsName() ?: return false

            val runCatchingNames = element.containingKtFile.runCatchingNamesWithAliases()
            return callName in runCatchingNames
        }

        // Otherwise this inspection is only active in try blocks
        return element is KtTryExpression && element.catchClauses.isNotEmpty()
    }

    /**
     * Returns whether any call expression inside the block calls a suspend method.
     */
    context(session: KaSession)
    private fun KtBlockExpression.callsSuspendMethod(suspendContextCache: MutableMap<PsiElement, KtFunction?>): Boolean {
        val calls = descendantsOfType<KtCallExpression>()
        for (call in calls) {
            val resolvedCall = call.resolveSuccessfulCall() ?: continue
            val functionSymbol = resolvedCall.symbol as? KaNamedFunctionSymbol ?: continue
            if (!functionSymbol.isSuspend) continue

            // We only consider suspend calls in the same suspend "context"
            val suspendContext = getContainingSuspendContext(call, suspendContextCache) ?: continue
            if (PsiTreeUtil.isAncestor(suspendContext, this, false)) return true
        }

        return false
    }

    /**
     * Returns true if the chain of calls starting from the [initialCall] might suppress a CancellationException.
     */
    context(session: KaSession)
    private fun checkResultChain(initialCall: KtQualifiedExpression): Boolean {
        var current: KtQualifiedExpression? = initialCall

        while (current != null) {
            val callOrAccess = current.selectorExpression
            val nextExpression = current.topParenthesizedParentOrMe().parent as? KtQualifiedExpression
            val resolvedCall = callOrAccess?.resolveSuccessfulExpressionCall() as? KaCallableMemberCall<*, *> ?: return false
            val callableId = resolvedCall.symbol.callableId

            // We are definitely suppressing the exception
            if (callableId in definitelyDiscardingCallables) return true

            if (resolvedCall is KaFunctionCall<*> && callableId in potentiallyHandlingCallables) {
                // We only check lambdas. Any function references are out of scope for this inspection.
                val functionLiterals = resolvedCall.combinedArgumentMapping.keys.filterIsInstance<KtLambdaExpression>()
                if (functionLiterals.isEmpty()) return false
                val returnType = resolvedCall.signature.returnType
                val returnsResult = returnType.isSubtypeOf(StandardClassIds.Result)
                val allSuppressLambda = functionLiterals.all { it.bodyExpression?.suppressesCancellationException() == true }

                // At least one of the lambdas might handle the result
                if (!allSuppressLambda) return false
                // None of the lambdas handle the result, and we no longer have a result after.
                // The exception is most likely suppressed.
                if (!returnsResult) return true
            }

            // We do not know if the callable suppresses the exception or not.
            // To avoid false positives, we disable the inspection.
            if (callableId !in nonExceptionHandlingCallables && callableId !in potentiallyHandlingCallables) return false

            if (nextExpression == null) {
                // If the topmost expression is used, then it is either stored in a variable or passed to a function.
                // It escapes our scope, so we have to disable the inspection.
                return !current.isUsedAsExpression
            }

            current = nextExpression
        }

        return true
    }

    /**
     * Checks if the `runCatching` together with its call chain might suppress a CancellationException
     * and returns data about the suppression if it does.
     */
    context(session: KaSession)
    private fun checkRunCatching(
        call: KtCallExpression,
        suspendContextCache: MutableMap<PsiElement, KtFunction?>
    ): SuppressedCancellationExceptionInspectionData? {
        val resolvedCall = call.resolveSuccessfulCall() ?: return null
        if (resolvedCall.symbol.callableId != StandardKotlinNames.Result.runCatching) return null

        val (parameter, _) = resolvedCall.combinedArgumentMapping.entries.firstOrNull { (_, signature) ->
            signature.name == RUN_CATCHING_BLOCK_PARAM
        } ?: return null

        // It is not possible to pass suspend function references to `runCatching`.
        // Anonymous functions are `KtNamedFunctions` and they always have a `bodyBlockExpression`.
        if (parameter !is KtLambdaExpression && parameter !is KtNamedFunction) return null

        val bodyExpression = when (parameter) {
            is KtLambdaExpression -> parameter.bodyExpression
            is KtNamedFunction -> parameter.bodyBlockExpression
            else -> null
        } ?: return null

        // We assume that a runCatching without calling a suspend function is not relevant to check here.
        if (!bodyExpression.callsSuspendMethod(suspendContextCache)) return null

        // If we do not use it as an expression at all, then we are definitely swallowing exceptions
        if (!call.isUsedAsExpression) return RunCatchingDetection(call)

        // We know the `runCatching` result is used in some way.
        // Check if we could suppress the cancellation exception
        val parentCall = call
            .getQualifiedExpressionForSelectorOrThis() // remove potential receiver
            .topParenthesizedParentOrMe() // remove parentheses
            .getQualifiedExpressionForReceiver() // get the next call if it exists

        // This means we are not below a dot-qualified expression, but we are used as an expression,
        // so the result must be used in some other way, and we disable the inspection.
        if (parentCall == null) return null

        if (checkResultChain(parentCall)) {
            return RunCatchingDetection(call)
        }
        return null
    }

    /**
     * Checks if a call is likely to only log the exception rather than also handling/re-throwing it.
     */
    context(session: KaSession)
    private fun KaFunctionCall<*>.isLoggingOnlyCall(): Boolean {
        val callableName = symbol.callableId?.callableName?.asString() ?: return false
        // These names suggest the exception is handled or re-thrown rather than only logged.
        if (HANDLING_NAME_PARTS.any { callableName.contains(it, ignoreCase = true) }) return false
        if (callableName.startsWith("log", ignoreCase = true)) return true

        // Calls on a logger instance, such as `LOG.error("...", e)`, where the name alone says nothing.
        val receiver = dispatchReceiver ?: extensionReceiver
        val receiverName = receiver?.type?.expandedSymbol?.classId?.shortClassName?.asString() ?: return false
        return receiverName.isLikelyLoggerReceiver()
    }

    /**
     * Checks if the passed call might raise a cancellation exception, we detect the following cases:
     * - Calls to `ensureActive`
     * - Calls containing `cancel` such as `checkCanceled()`
     * - Calls returning `Nothing`, which often indicates it throwing an exception
     */
    context(session: KaSession)
    private fun KaFunctionCall<*>.mayRaiseCancellation(): Boolean {
        val functionName = symbol.callableId?.callableName?.asString() ?: ""
        if (functionName.contains("cancel", ignoreCase = true)) {
            return true
        }
        return symbol.callableId == CoroutinesIds.ensureActive || signature.returnType.classId == StandardClassIds.Nothing
    }

    /**
     * See [mayRaiseCancellationAfter] and [suppressesCancellationException].
     */
    context(session: KaSession)
    private fun KtExpression.mayDescendantRaiseCancellation(additionalChecks: (KaFunctionCall<*>) -> Boolean = { false }): Boolean {
        if (anyDescendantOfType<KtThrowExpression>()) return true
        val calls = descendantsOfType<KtCallExpression>()
        for (call in calls) {
            val resolvedCall = call.resolveSuccessfulCall() ?: continue
            if (resolvedCall.mayRaiseCancellation()) {
                return true
            }
            if (additionalChecks(resolvedCall)) return true
        }
        return false
    }

    /**
     * We check if a handler block suppresses the exception it received.
     * The caught exception is in scope here, so it is not suppressed if
     *  - there is a throw expression inside the block,
     *  - the block calls something that always throws,
     *  - the block calls `ensureActive`,
     *  - or the exception is passed to any function that does more than logging.
     */
    context(session: KaSession)
    private fun KtBlockExpression.suppressesCancellationException(): Boolean {
        return !mayDescendantRaiseCancellation { resolvedCall ->
            val consumesThrowable = resolvedCall.combinedArgumentMapping.values
                .any { it.returnType.isSubtypeOf(StandardClassIds.Throwable) }
            consumesThrowable && !resolvedCall.isLoggingOnlyCall()
        }
    }

    /**
     * Checks if the code that might be executed after/"below" the given element may raise a cancellation exception.
     * Given the containing block and all of its parents in the same function, we check all statements "below" the element
     * to see if they may raise a cancellation exception, see [mayRaiseCancellation].
     */
    context(session: KaSession)
    private fun KtElement.mayRaiseCancellationAfter(): Boolean {
        var current: KtElement? = this
        while (current != null) {
            // We reached the boundary of our search scope
            if (current is KtPropertyAccessor || current is KtNamedFunction || current is KtConstructor<*>) return false
            if (current is KtFunctionLiteral && !isInlinedArgument(current, allowCrossinline = false)) return false

            // Special case for finally blocks because they always get executed after
            if (current is KtTryExpression) {
                val finallyBlock = current.finallyBlock
                // The special case is only necessary if we did not come from the finally block before
                val isDescendantOfFinally = PsiTreeUtil.isAncestor(finallyBlock, this, true)
                if (finallyBlock != null && !isDescendantOfFinally && finallyBlock.finalExpression.mayDescendantRaiseCancellation()) {
                    return true
                }
            }

            if (current.parent is KtBlockExpression) {
                // We only have statements of relevance following our current element if we are in a block.
                // E.g., an if branch with an expression body does not have statements following it.
                val siblingsInBlock = current.siblings(forward = true, withSelf = false)
                    .filterIsInstance<KtExpression>()
                for (statement in siblingsInBlock) {
                    if (statement.mayDescendantRaiseCancellation()) {
                        return true
                    }
                }
            }

            current = current.parent as? KtElement
        }
        return false
    }

    /**
     * Returns the first catch clause within [this] try expression that a `CancellationException` would be handled by
     * as long as the caught exception type is a proper supertype of `CancellationException`.
     * If a catch clause is found handling a subtype of `CancellationException` (or itself),
     * we assume that the user handles `CancellationExceptions` correctly and return null.
     */
    context(session: KaSession)
    private fun KtTryExpression.findClauseCatchingCancellation(): KtCatchClause? {
        val cancellationType = session.typeCreator.classType(CoroutinesIds.Stdlib.Cancellation.CancellationException.ID)
        for (catchClause in catchClauses) {
            val exceptionType = catchClause.catchParameter?.typeReference?.type ?: continue
            if (exceptionType.isPossiblySubTypeOf(cancellationType)) {
                // This means someone is handling a subtype of `CancellationException` explicitly, which
                // means the user did not forget about it and might be handling it even without throwing it.
                return null
            }
            if (cancellationType.isPossiblySubTypeOf(exceptionType)) {
                return catchClause
            }
        }
        return null
    }

    /**
     * Checks if the [tryExpression] might suppress a CancellationException by catching it
     * without rethrowing it and returns data about the suppression if it does.
     */
    context(session: KaSession)
    private fun checkTryExpression(
        tryExpression: KtTryExpression,
        suspendContextCache: MutableMap<PsiElement, KtFunction?>
    ): SuppressedCancellationExceptionInspectionData? {
        // First, check if there is a catch clause that handles `CancellationException`
        val handlingClause = tryExpression.findClauseCatchingCancellation() ?: return null

        // We only check try blocks that actually call a suspend function, but we do it after
        // because it is heavier than the check above.
        if (!tryExpression.tryBlock.callsSuspendMethod(suspendContextCache)) return null

        val catchBlock = handlingClause.catchBody as? KtBlockExpression ?: return null
        if (catchBlock.suppressesCancellationException()) {
            return TryCatchDetection(tryExpression, handlingClause)
        }

        return null
    }

    /**
     * Returns all the implicit receivers that are exposed from within the [suspendContext].
     * Omits any implicit receivers having their origin outside the [suspendContext].
     */
    private fun KaScopeContext.implicitReceiversOfCurrentContext(suspendContext: KtFunction): List<KaImplicitReceiver> {
        return implicitReceivers.filter {
            val ownerPsi = it.ownerSymbol.psi ?: return@filter false
            PsiTreeUtil.isAncestor(suspendContext, ownerPsi, false)
        }
    }

    /**
     * `SequenceBuilder` and `DeepRecursive` both restrict suspension because they do not
     * actually use the Coroutines API. Instead, they only use the CPS transformation to
     * model possibly infinite runs of code.
     * In these cases, cancellation exceptions are not expected as regular suspend functions cannot be called.
     */
    private fun KaScopeContext.isInRestrictedSuspensionBlock(suspendContext: KtFunction): Boolean {
        return implicitReceiversOfCurrentContext(suspendContext).any {
            it.type.symbol?.annotations?.contains(StandardClassIds.Annotations.RestrictsSuspension) == true
        }
    }

    /**
     * Returns whether there is an implicit receiver of type `CoroutineScope` that is exposed from within the [suspendContext].
     */
    context(_: KaSession)
    private fun KaScopeContext.hasImplicitCoroutineScopeReceiver(suspendContext: KtFunction): Boolean {
        return implicitReceiversOfCurrentContext(suspendContext).any {
            it.type.isSubtypeOf(CoroutinesIds.CoroutineScope.ID)
        }
    }

    context(session: KaSession)
    override fun prepareContext(element: KtExpression): SuppressedCancellationExceptionInspectionData? {
        // We use this cache in `isInSuspendContext` and `getContainingSuspendContext` which
        // might be called for many elements that share the same suspend context, so caching
        // will provide a speed-up.
        val suspendContextCache = mutableMapOf<PsiElement, KtFunction?>()
        val suspendContext = getContainingSuspendContext(element, suspendContextCache)
        if (suspendContext == null) {
            return null
        }

        val detection = when (element) {
            is KtCallExpression -> {
                checkRunCatching(element, suspendContextCache)
            }

            is KtTryExpression -> {
                checkTryExpression(element, suspendContextCache)
            }

            else -> null
        } ?: return null

        // We do the following checks after running the detection because they are heavy performance wise
        if (element.mayRaiseCancellationAfter()) {
            // The code after the `runCatching` or `catch` already potentially re-raises the CancellationException
            return null
        }
        val scopeContext = element.containingKtFile.scopeContext(element)
        if (scopeContext.isInRestrictedSuspensionBlock(suspendContext)) return null

        detection.hasImplicitCoroutineScopeReceiver = scopeContext.hasImplicitCoroutineScopeReceiver(suspendContext)
        return detection
    }
}

/**
 * Creates the PSI for calling `ensureActive` on the current coroutine context, or using
 * the available implicit receiver if [hasImplicitCoroutineScopeReceiver] is set to true.
 * If [exceptionParameterName] is not "_", wraps the call in an `if` statement checking if the caught exception is a CancellationException.
 * Adds an import for `ensureActive` to the [ktFile] if required.
 */
private fun createEnsureActiveCallAndImportIfNecessary(
    exceptionParameterName: String,
    hasImplicitCoroutineScopeReceiver: Boolean,
    psiFactory: KtPsiFactory,
    ktFile: KtFile
): KtExpression {
    val ensureActiveFqName = CoroutinesIds.ensureActive.asSingleFqName()
    // `ensureActive` is an extension function with a receiver and needs to be imported explicitly.
    ktFile.addImportFor(ensureActiveFqName)

    val ensureActiveCall = if (hasImplicitCoroutineScopeReceiver) {
        psiFactory.createExpressionByPattern("$0()", ensureActiveFqName.shortName())
    } else {
        val currentCoroutineContextFqName = CoroutinesIds.currentCoroutineContext.asSingleFqName().withRootPrefixIfNeeded()
        psiFactory.createExpressionByPattern("$0().$1()", currentCoroutineContextFqName.render(), ensureActiveFqName.shortName())
    }

    if (exceptionParameterName == "_") {
        // Without exception name, we do not check the exception to be a CancellationException explicitly.
        return ensureActiveCall
    }

    val cancellationExceptionFqName = CoroutinesIds.CancellationException.ID.asSingleFqName().withRootPrefixIfNeeded()
    val expressionCheck = psiFactory.createExpression("$exceptionParameterName is ${cancellationExceptionFqName.render()}")
    val thenBlock = psiFactory.createBlock(ensureActiveCall.text)
    return psiFactory.createIf(expressionCheck, thenBlock)
}

private class AddEnsureActiveInRunCatchingQuickFix(
    private val hasImplicitCoroutineScopeReceiver: Boolean
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("check.for.cancellation.run.catching.text")

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(project)

        val ifExpression = createEnsureActiveCallAndImportIfNecessary(
            exceptionParameterName = "it",
            hasImplicitCoroutineScopeReceiver = hasImplicitCoroutineScopeReceiver,
            psiFactory = psiFactory,
            ktFile = element.containingKtFile
        )
        val newExpression = psiFactory.createExpressionByPattern("$0.onFailure { $1 }", element, ifExpression)
        val replaced = element.replaced(newExpression)

        val onFailureExpression = (replaced as? KtDotQualifiedExpression)?.callExpression ?: return
        shortenReferences(onFailureExpression)
    }
}

private class AddEnsureActiveToTryCatchQuickFix(
    private val catchClauseIndex: Int,
    private val hasImplicitCoroutineScopeReceiver: Boolean,
) : KotlinModCommandQuickFix<KtTryExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("check.for.cancellation.try.catch.text")

    override fun applyFix(
        project: Project,
        element: KtTryExpression,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(project)
        val catchClause = element.catchClauses.getOrNull(catchClauseIndex) ?: return
        val catchBody = catchClause.catchBody as? KtBlockExpression ?: return
        val lbrace = catchBody.lBrace ?: return
        val parameterName = catchClause.catchParameter?.name ?: return

        val ifExpression = createEnsureActiveCallAndImportIfNecessary(
            exceptionParameterName = parameterName,
            hasImplicitCoroutineScopeReceiver = hasImplicitCoroutineScopeReceiver,
            psiFactory = psiFactory,
            ktFile = element.containingKtFile
        )

        val newIfStatement = catchBody.addAfter(ifExpression, lbrace) as? KtElement
        if (newIfStatement != null) {
            shortenReferences(newIfStatement)
        }
    }
}
