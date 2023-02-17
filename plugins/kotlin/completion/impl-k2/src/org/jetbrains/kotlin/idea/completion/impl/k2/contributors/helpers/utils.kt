// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.StandardNames
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
    excludeEnumEntries: Boolean,
    skipJavaGettersAndSetters: Boolean = true,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSymbol> {
    // make the filter aware of prefixes
    // for example, a variable with the name `prop` satisfies the filter for all the following prefixes: "p", "getP", "setP"
    val getAndSetPrefixesAwareFilter: KtScopeNameFilter =
        { name -> listOfNotNull(name, name.toJavaGetterName(), name.toJavaSetterName()).any(scopeNameFilter) }

    val innerClasses = scope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KtNamedClassOrObjectSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.getDeclaredMemberScope().getConstructors() }

    val syntheticProperties = syntheticJavaPropertiesScope
        ?.getCallableSymbols(getAndSetPrefixesAwareFilter)
        ?.filterIsInstance<KtSyntheticJavaPropertySymbol>()
        .orEmpty()

    val callableSymbols = scope.getCallableSymbols(getAndSetPrefixesAwareFilter)

    val nonExtensions = (innerClassesConstructors + syntheticProperties + callableSymbols)
        .filterNot { it.isExtension }
        .filter { symbolFilter(it) }
        .filter { visibilityChecker.isVisible(it) }

    return nonExtensions
        .applyIf(skipJavaGettersAndSetters) {
            val javaGettersAndSetters = syntheticProperties.flatMap { listOfNotNull(it.javaGetterSymbol, it.javaSetterSymbol) }.toSet()
            filter { it !in javaGettersAndSetters }
        }
        .applyIf(excludeEnumEntries) { filterNot(::isEnumEntriesProperty) }
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

private fun KtAnalysisSession.isEnumEntriesProperty(symbol: KtCallableSymbol): Boolean {
    return symbol is KtPropertySymbol &&
            symbol.isStatic &&
            symbol.callableIdIfNonLocal?.callableName == StandardNames.ENUM_ENTRIES &&
            (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.ENUM_CLASS
}
