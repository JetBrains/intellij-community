// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
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
): Sequence<KtCallableSignatureWithContainingScopeKind> = sequence {
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
                KtCallableSignatureWithContainingScopeKind(it, kind)
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
): Sequence<KtCallableSignatureWithContainingScopeKind> {
    val typeScope = type.getTypeScope() ?: return emptySequence()
    val syntheticJavaPropertiesTypeScope = type.takeIf { withSyntheticJavaProperties }?.getSyntheticJavaPropertiesScope()

    val getAndSetAwareNameFilter = scopeNameFilter.getAndSetAware()

    val syntheticProperties = syntheticJavaPropertiesTypeScope?.getCallableSignatures(getAndSetAwareNameFilter)
        ?.filterNonExtensions(visibilityChecker, symbolFilter)
        ?.filterIsInstance<KtCallableSignature<KtSyntheticJavaPropertySymbol>>()
        .orEmpty()

    val callables = typeScope.getCallableSignatures(getAndSetAwareNameFilter).applyIf(skipJavaGettersAndSetters) {
        val javaGetterAndSetterSymbols = syntheticProperties.flatMapTo(mutableSetOf()) { it.symbol.getterAndSetter }
        filter { it.symbol !in javaGetterAndSetterSymbols }
    }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KtNamedClassOrObjectSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.getDeclaredMemberScope().getConstructors() }.map { it.asSignature() }

    val nonExtensionsFromType = (callables + innerClassesConstructors).filterNonExtensions(visibilityChecker, symbolFilter)

    return sequence<KtSymbolWithContainingScopeKind<KtCallableSymbol>> {
        yieldAll(nonExtensionsFromType.map {
            KtCallableSignatureWithContainingScopeKind(it, indexInTower?.let { KtScopeKind.SimpleTypeScope(indexInTower) })
        })
        yieldAll(syntheticProperties.map {
            KtCallableSignatureWithContainingScopeKind(it, indexInTower?.let { KtScopeKind.SyntheticJavaPropertiesScope(indexInTower) })
        })
    }.applyIf(excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.signature.symbol) } }
}

context(KtAnalysisSession)
private val KtSyntheticJavaPropertySymbol.getterAndSetter: List<KtCallableSymbol>
    get() = listOfNotNull(javaGetterSymbol, javaSetterSymbol)

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
): Sequence<KtCallableSignature<*>> = scope.getCallableSymbols(scopeNameFilter.getAndSetAware())
    .map { it.asSignature() }
    .filterNonExtensions(visibilityChecker, symbolFilter)
    .applyIf(excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.symbol) } }

context(KtAnalysisSession)
private fun Sequence<KtCallableSignature<*>>.filterNonExtensions(
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KtCallableSymbol) -> Boolean = { true }
): Sequence<KtCallableSignature<*>> = this
    .filterNot { it.symbol.isExtension }
    .filter { symbolFilter(it.symbol) }
    .filter { with(visibilityChecker) { isVisible(it.symbol) } }

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