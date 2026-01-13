// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name

internal class K2CompletionContext<out P: KotlinRawPositionContext>(
    val parameters: KotlinFirCompletionParameters,
    val resultSet: CompletionResultSet,
    val positionContext: P,
) {
    internal val prefixMatcher = resultSet.prefixMatcher

    internal val scopeNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && prefixMatcher.prefixMatches(name.identifier) }

    // Prefix matcher that only matches if the completion item starts with the prefix.
    private val startOnlyMatcher by lazy { BetterPrefixMatcher(prefixMatcher, Int.MIN_VALUE) }
    private val startOnlyNameFilter: (Name) -> Boolean =
        { name -> !name.isSpecial && startOnlyMatcher.prefixMatches(name.identifier) }

    /**
     * Returns the name filter that should be used for index lookups.
     * If the prefix is less than four characters, we do not use the regular [scopeNameFilter] as it will
     * match occurrences anywhere in the name, which might yield too many results.
     * For other cases (unless the user invokes completion multiple times outside a rerun), this function will return
     * the [startOnlyNameFilter] that requires a match at the start of the lookup item's lookup strings.
     */
    internal fun getIndexNameFilter(): (Name) -> Boolean {
        return if ((parameters.invocationCount >= 2 && !parameters.isRerun) || prefixMatcher.prefix.length > 3) {
            scopeNameFilter
        } else {
            startOnlyNameFilter
        }
    }

    internal val targetPlatform = parameters.originalFile.platform

    internal val originalFile = parameters.originalFile
}