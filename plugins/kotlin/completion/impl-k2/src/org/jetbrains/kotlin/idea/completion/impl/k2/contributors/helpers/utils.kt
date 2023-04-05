// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
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
internal fun KtAnalysisSession.collectNonExtensionsFromScopeContext(
    scopeContext: KtScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    excludeEnumEntries: Boolean,
    withSyntheticJavaProperties: Boolean = true,
    skipJavaGettersAndSetters: Boolean = true,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtSymbolWithContainingScopeKind<KtCallableSymbol>> = sequence {
    val indexedImplicitReceivers = scopeContext.implicitReceivers.associateBy { it.scopeIndexInTower }

    for (scopeWithKind in scopeContext.scopes) {
        val kind = scopeWithKind.kind
        val collectWithSyntheticJavaProperties =
            kind is KtScopeKind.TypeScope && withSyntheticJavaProperties && kind.indexInTower in indexedImplicitReceivers

        val nonExtensions = if (collectWithSyntheticJavaProperties) {
            val implicitReceiver = indexedImplicitReceivers.getValue(kind.indexInTower)
            collectNonExtensionsForType(
                implicitReceiver.type,
                visibilityChecker,
                scopeNameFilter,
                excludeEnumEntries,
                withSyntheticJavaProperties = true,
                skipJavaGettersAndSetters,
                implicitReceiver.scopeIndexInTower,
                symbolFilter
            )
        } else {
            collectNonExtensionsFromScope(scopeWithKind.scope, visibilityChecker, scopeNameFilter, excludeEnumEntries, symbolFilter).map {
                KtSymbolWithContainingScopeKind(it, kind)
            }
        }
        yieldAll(nonExtensions)
    }
}

/**
 * @param skipJavaGettersAndSetters if true, skips Java getters and setters that are mapped to Kotlin properties.
 * @param indexInTower index of implicit receiver's scope in scope tower if it is known, otherwise null.
 */
internal fun KtAnalysisSession.collectNonExtensionsForType(
    type: KtType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    excludeEnumEntries: Boolean,
    withSyntheticJavaProperties: Boolean = true,
    skipJavaGettersAndSetters: Boolean = true,
    indexInTower: Int? = null,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtSymbolWithContainingScopeKind<KtCallableSymbol>> {
    val typeScope = type.getTypeScope()?.getDeclarationScope() ?: return emptySequence()
    val syntheticJavaPropertiesScope = type.takeIf { withSyntheticJavaProperties }
        ?.getSyntheticJavaPropertiesScope()
        ?.getDeclarationScope()

    val syntheticProperties = syntheticJavaPropertiesScope?.let {
        collectNonExtensionsFromScope(
            it,
            visibilityChecker,
            scopeNameFilter,
            excludeEnumEntries,
            symbolFilter
        ).filterIsInstance<KtSyntheticJavaPropertySymbol>()
    }.orEmpty()

    val callableSymbols = typeScope.getCallableSymbols(scopeNameFilter.getAndSetAware()).applyIf(skipJavaGettersAndSetters) {
        val javaGettersAndSetters = syntheticProperties.flatMap { listOfNotNull(it.javaGetterSymbol, it.javaSetterSymbol) }.toSet()
        filter { it !in javaGettersAndSetters }
    }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KtNamedClassOrObjectSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.getDeclaredMemberScope().getConstructors() }

    val nonExtensionsFromType = (callableSymbols + innerClassesConstructors).filterNonExtensions(visibilityChecker, symbolFilter)

    return sequence<KtSymbolWithContainingScopeKind<KtCallableSymbol>> {
        yieldAll(nonExtensionsFromType.map {
            KtSymbolWithContainingScopeKind(it, indexInTower?.let { KtScopeKind.SimpleTypeScope(indexInTower) })
        })
        yieldAll(syntheticProperties.map {
            KtSymbolWithContainingScopeKind(it, indexInTower?.let { KtScopeKind.SyntheticJavaPropertiesScope(indexInTower) })
        })
    }.applyIf(excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.symbol) } }
}

/**
 * Returns non-extensions from [KtScope]. Resulting callables do not include synthetic Java properties and constructors of inner classes.
 * To get them use [collectNonExtensionsForType].
 */
internal fun KtAnalysisSession.collectNonExtensionsFromScope(
    scope: KtScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    excludeEnumEntries: Boolean,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSymbol> = scope.getCallableSymbols(scopeNameFilter.getAndSetAware())
    .filterNonExtensions(visibilityChecker, symbolFilter)
    .applyIf(excludeEnumEntries) { filterNot { isEnumEntriesProperty(it) } }

context(KtAnalysisSession)
private fun Sequence<KtCallableSymbol>.filterNonExtensions(
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSymbol> = this
    .filterNot { it.isExtension }
    .filter { symbolFilter(it) }
    .filter { with(visibilityChecker) { isVisible(it) } }

/**
 * Returns a filter aware of prefixes. For example, a variable with the name `prop` satisfies the filter for all the following prefixes:
 * "p", "getP", "setP"
 */
private fun KtScopeNameFilter.getAndSetAware(): KtScopeNameFilter = { name ->
    listOfNotNull(name, name.toJavaGetterName(), name.toJavaSetterName()).any(this)
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