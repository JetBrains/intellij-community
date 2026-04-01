// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.FusCompletionKeys.LOOKUP_ELEMENT_CONTRIBUTOR
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.codeInsight.contributorClass

@ApiStatus.Internal
class KotlinGradleCleanupCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, insideScriptBlockPattern(DEPENDENCIES), RemainingCompletionContributorsFilterer())
    }
}

private class RemainingCompletionContributorsFilterer : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (parameters.invocationCount >= 2) return
        // Hide ignored contributors for autocompletion (0 invocations) and first-time invoked completion
        result.runRemainingContributors(parameters) { otherResult ->
            if (!isContributorIgnored(otherResult)) {
                result.passResult(otherResult)
            }
        }
    }
}

private fun isContributorIgnored(otherResult: CompletionResult): Boolean {
    val lookupElement = otherResult.lookupElement
    val contributorName = lookupElement.getUserData(LOOKUP_ELEMENT_CONTRIBUTOR)?.javaClass?.name
    return contributorName in ignoredContributors
            || isProducedByK2Contributor(lookupElement)
}

private fun isProducedByK2Contributor(lookupElement: LookupElement): Boolean {
    val contributorClass = lookupElement.contributorClass ?: return false
    return generateSequence(contributorClass) { it.superclass }
        .any { it.name == "org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionContributor" }
}

private val ignoredContributors = setOf(
    "com.intellij.codeInsight.completion.LegacyCompletionContributor"
)
