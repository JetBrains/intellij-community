// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

internal fun createStarTypeArgumentsList(typeArgumentsCount: Int): String =
    if (typeArgumentsCount > 0) {
        List(typeArgumentsCount) { "*" }.joinToString(prefix = "<", postfix = ">")
    } else {
        ""
    }

internal fun KtAnalysisSession.collectNonExtensions(
    scope: KtScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSymbol> = scope.getCallableSymbols { name ->
    listOfNotNull(name, name.toJavaGetterName(), name.toJavaSetterName()).any(scopeNameFilter)
}
    .filterNot { it.isExtension }
    .filter { symbolFilter(it) }
    .filter { with(visibilityChecker) { isVisible(it) } }


private fun Name.toJavaGetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.getterName(it)) }
private fun Name.toJavaSetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.setterName(it)) }
