// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.resolution.KaCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.K2SmartCompletionTailOffsetProviderImpl
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.Tail
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.tryGetOffset
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * A completion contributor that is responsible for completing multiple arguments to function calls or array access at once.
 * This contributor only takes into account local variables that exactly match the name of the parameters.
 * We only consider variables that are in-scope within the local file (e.g., no imported values).
 *
 * The contributor only generates items if at least 2 arguments could be matched (single variables already appear in regular completion).
 */
internal class K2MultipleArgumentContributor : K2SimpleCompletionContributor<KotlinNameReferencePositionContext>(
    KotlinNameReferencePositionContext::class
) {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    override fun shouldExecute(): Boolean {
        return context.positionContext.explicitReceiver == null
    }

    /**
     * For the arguments we care about two kinds of calls,
     * 1. Function calls (regular functions, super calls, etc.)
     * 2. Array index expressions
     *
     * Given the argument as [this], the function returns the parent expression that
     * can be used with the analysis API to resolve the call candidates, or null if none could be found.
     */
    private fun KtElement.getAppropriateCallParent(): KtElement? {
        val nameExpressionParent = parent
        return when {
            nameExpressionParent is KtValueArgument -> {
                val valueArgumentList = nameExpressionParent.parent as? KtValueArgumentList ?: return null
                // This contributor is only enabled for the last argument of either calls or array access expressions
                if (valueArgumentList.arguments.lastOrNull() != nameExpressionParent) return null
                valueArgumentList.parent as? KtElement
            }

            nameExpressionParent.parent is KtArrayAccessExpression -> {
                val arrayAccessExpression = nameExpressionParent.parent as? KtArrayAccessExpression ?: return null
                // This contributor is only enabled for the last argument of either calls or array access expressions
                if (arrayAccessExpression.indexExpressions.lastOrNull() != this) return null
                arrayAccessExpression
            }

            else -> null
        }
    }

    private data class MissingArgumentData(
        val signature: KaFunctionSignature<*>,
        val missingArguments: Map<Name, KaType>
    )

    /**
     * Given the [callCandidates], calculates the signatures together with their missing arguments.
     */
    context(_: KaSession)
    private fun getApplicableSignatures(callCandidates: List<KaCallCandidateInfo>): List<MissingArgumentData> {
        val signatures: MutableList<MissingArgumentData> = mutableListOf()

        for (candidate in callCandidates) {
            val applicableCandidate = candidate.candidate as? KaFunctionCall<*> ?: continue
            // We consider the currently completed argument as not passed
            // Note applicableCandidate.valueArgumentMapping is ordered by argument index
            val alreadyPassedParameters = applicableCandidate.valueArgumentMapping.values.toList().dropLast(1)
            val missingValueParameters = applicableCandidate.signature.valueParameters.filterNot { it in alreadyPassedParameters }
            // For this contributor, we need at least 2 missing arguments
            if (missingValueParameters.size <= 1) continue

            val missingArgumentMapping = missingValueParameters.associateBy({ it.name }, { it.returnType })

            signatures.add(MissingArgumentData(applicableCandidate.signature, missingArgumentMapping))
        }

        return signatures
    }

    /**
     * Returns local variables (i.e., from the same file) with the names from [allNamesToFind].
     * Note that shadowing is taken into account properly, and only the closest variable that is available is returned
     * for each name.
     */
    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun getVariablesForNames(allNamesToFind: Set<Name>): Map<Name, KaVariableSymbol> {
        // We do not want to consider variables that are imported
        val scopes = context.weighingContext.scopeContext.scopes.filterNot { it.kind is KaScopeKind.ImportingScope }

        // We reverse the scopes to correctly perform shadowing.
        // Innermost scopes appear first in `scopes` so for their variables to win, we need to reverse the list.
        val variables = scopes.reversed().flatMap { scopeWithKind ->
            scopeWithKind.scope.callables(allNamesToFind)
                .filterIsInstance<KaVariableSymbol>()
                .map { it.name to it }
        }.toMap()

        return variables
    }

    data class MultiArgumentSignatureData(
        val completesAllArguments: Boolean,
        val tail: Tail,
        val matchingVariables: List<KaVariableSymbol>,
    )

    /**
     * Attempts to match the [variableSymbols] with the given [missingArgumentData].
     * This function will match the first missing arguments (from left-to-right) until an argument can no longer be matched
     * or until all arguments have been matched and return the resulting matching variables.
     * Note that this function only returns the longest possible match unlike in K1 to avoid cluttering up completion too much.
     */
    context(_: KaSession)
    private fun matchVariablesWithMissingArguments(
        variableSymbols: Map<Name, KaVariableSymbol>,
        missingArgumentData: MissingArgumentData,
        callParent: KtElement,
    ): MultiArgumentSignatureData? {
        val matchingVariableSymbols = mutableListOf<KaVariableSymbol>()
        for ((name, type) in missingArgumentData.missingArguments) {
            val correspondingVariable = variableSymbols[name] ?: break
            // We use isPossiblySubTypeOf until KT-84184 is fixed to avoid false-negatives
            if (!correspondingVariable.returnType.isPossiblySubTypeOf(type)) break
            matchingVariableSymbols.add(correspondingVariable)
        }

        val matchingVariableCount = matchingVariableSymbols.size
        if (matchingVariableCount < 2) return null

        val completesAllArguments = matchingVariableCount == missingArgumentData.missingArguments.size
        val tail = when {
            !completesAllArguments -> Tail.COMMA
            callParent is KtArrayAccessExpression -> Tail.RBRACKET
            else -> Tail.RPARENTH
        }

        return MultiArgumentSignatureData(completesAllArguments, tail, matchingVariableSymbols)
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    override fun complete() {
        val callParent = context.positionContext.nameExpression.getAppropriateCallParent() ?: return
        val callCandidates = callParent.resolveToCallCandidates()
        if (callCandidates.isEmpty()) return

        val signatures = getApplicableSignatures(callCandidates)
        if (signatures.isEmpty()) return

        val allNamesToFind = signatures.flatMapTo(mutableSetOf()) { it.missingArguments.keys }
        val variables = getVariablesForNames(allNamesToFind)

        val matchedNames = mutableMapOf<List<Name>, MultiArgumentSignatureData>()
        for (signature in signatures) {
            val matchedSignature = matchVariablesWithMissingArguments(
                variableSymbols = variables,
                missingArgumentData = signature,
                callParent = callParent,
            ) ?: continue

            val matchingVariableNames = matchedSignature.matchingVariables.map { it.name }

            // For each list of found variable names, we prefer the signatures that would lead to a complete call.
            // Note: this only matters for tail handling because we can then move out of the call as we are finished with it.
            matchedNames.compute(matchingVariableNames) { _, existingData ->
                if (existingData != null && existingData.completesAllArguments) return@compute existingData
                matchedSignature
            }
        }

        for ((_, data) in matchedNames) {
            addElement(KotlinFirLookupElementFactory.createMultipleArgumentsLookupElement(data.matchingVariables, data.tail))
        }
    }
}

/**
 * This insertion handler is responsible for replacing the existing arguments
 * if replacement completion is invoked with a multi-argument completion item.
 *
 * e.g.: foo(<caret>a, b) -> foo(c, d)<caret>
 */
@Serializable
internal class MultipleArgumentsInsertHandler : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        if (context.completionChar != Lookup.REPLACE_SELECT_CHAR) return
        val offset = context.offsetMap.tryGetOffset(K2SmartCompletionTailOffsetProviderImpl.MULTIPLE_ARGUMENTS_REPLACEMENT_OFFSET)
        if (offset != null) {
            context.document.deleteString(context.tailOffset, offset)
        }
    }

}