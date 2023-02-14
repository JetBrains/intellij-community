// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

internal fun createStarTypeArgumentsList(typeArgumentsCount: Int): String =
    if (typeArgumentsCount > 0) {
        List(typeArgumentsCount) { "*" }.joinToString(prefix = "<", postfix = ">")
    } else {
        ""
    }

/**
 * @param skipJavaGettersAndSetters if true, skips Java getters and setters that are mapped to Kotlin properties.
 */
internal fun KtAnalysisSession.collectNonExtensions(
    scope: KtScope,
    syntheticJavaPropertiesScope: KtScope?,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    skipJavaGettersAndSetters: Boolean = true,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSymbol> {
    // make the filter aware of prefixes
    // for example, a variable with the name `prop` satisfies the filter for all the following prefixes: "p", "getP", "setP"
    val getAndSetPrefixesAwareFilter: KtScopeNameFilter =
        { name -> listOfNotNull(name, name.toJavaGetterName(), name.toJavaSetterName()).any(scopeNameFilter) }

    val syntheticProperties = syntheticJavaPropertiesScope
        ?.getCallableSymbols(getAndSetPrefixesAwareFilter)
        ?.filterIsInstance<KtSyntheticJavaPropertySymbol>()
        .orEmpty()

    val nonExtensions = sequence {
        yieldAll(syntheticProperties)
        yieldAll(scope.getCallableSymbols(getAndSetPrefixesAwareFilter))
    }.filter { !it.isExtension && symbolFilter(it) && visibilityChecker.isVisible(it) }

    return if (skipJavaGettersAndSetters) {
        val javaGettersAndSetters = syntheticProperties.flatMap { listOfNotNull(it.javaGetterSymbol, it.javaSetterSymbol) }.toSet()

        nonExtensions.filter { it !in javaGettersAndSetters }
    } else {
        nonExtensions
    }
}

private fun Name.toJavaGetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.getterName(it)) }
private fun Name.toJavaSetterName(): Name? = identifierOrNullIfSpecial?.let { Name.identifier(JvmAbi.setterName(it)) }

internal fun KtDeclaration.canDefinitelyNotBeSeenFromOtherFile(): Boolean {
    return when {
        isPrivate() -> true
        hasModifier(KtTokens.INTERNAL_KEYWORD) && containingKtFile.isCompiled -> {
            // internal declarations from library are invisible from source modules
            true
        }

        else -> false
    }
}