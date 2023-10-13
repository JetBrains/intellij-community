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
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

/**
 * Origin of [KtSymbol] used in completion suggestion
 */
internal sealed class CompletionSymbolOrigin {
    class Scope(val kind: KtScopeKind) : CompletionSymbolOrigin()

    object Index : CompletionSymbolOrigin()

    companion object {
        const val SCOPE_OUTSIDE_TOWER_INDEX: Int = -1
    }
}

internal fun createStarTypeArgumentsList(typeArgumentsCount: Int): String =
    if (typeArgumentsCount > 0) {
        List(typeArgumentsCount) { "*" }.joinToString(prefix = "<", postfix = ">")
    } else {
        ""
    }

/**
 * @param skipJavaGettersAndSetters if true, skips Java getters and setters that are mapped to Kotlin properties.
 */
context(KtAnalysisSession)
internal fun collectLocalAndMemberNonExtensionsFromScopeContext(
    scopeContext: KtScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KtCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = sequence {
    val indexedImplicitReceivers = scopeContext.implicitReceivers.associateBy { it.scopeIndexInTower }
    val scopes = scopeContext.scopes.filter { it.kind is KtScopeKind.LocalScope || it.kind is KtScopeKind.TypeScope }

    for (scopeWithKind in scopes) {
        val kind = scopeWithKind.kind
        val isImplicitReceiverScope = kind is KtScopeKind.TypeScope && kind.indexInTower in indexedImplicitReceivers

        val nonExtensions = if (isImplicitReceiverScope) {
            val implicitReceiver = indexedImplicitReceivers.getValue(kind.indexInTower)
            collectNonExtensionsForType(
                implicitReceiver.type,
                visibilityChecker,
                scopeNameFilter,
                sessionParameters,
                implicitReceiver.scopeIndexInTower,
                symbolFilter,
            )
        } else {
            collectNonExtensionsFromScope(scopeWithKind.scope, visibilityChecker, scopeNameFilter, sessionParameters, symbolFilter).map {
                KtCallableSignatureWithContainingScopeKind(it, kind)
            }
        }
        yieldAll(nonExtensions)
    }
}

context(KtAnalysisSession)
internal fun collectStaticAndTopLevelNonExtensionsFromScopeContext(
    scopeContext: KtScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KtCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = scopeContext.scopes.asSequence()
    .filterNot { it.kind is KtScopeKind.LocalScope || it.kind is KtScopeKind.TypeScope }
    .flatMap { scopeWithKind ->
        collectNonExtensionsFromScope(scopeWithKind.scope, visibilityChecker, scopeNameFilter, sessionParameters, symbolFilter)
            .map { KtCallableSignatureWithContainingScopeKind(it, scopeWithKind.kind) }
    }

/**
 * @param skipJavaGettersAndSetters if true, skips Java getters and setters that are mapped to Kotlin properties.
 * @param indexInTower index of implicit receiver's scope in scope tower if it is known, otherwise null.
 */
context(KtAnalysisSession)
internal fun collectNonExtensionsForType(
    type: KtType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    sessionParameters: FirCompletionSessionParameters,
    indexInTower: Int? = null,
    symbolFilter: (KtCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> {
    val typeScope = type.getTypeScope() ?: return emptySequence()

    val callables = typeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .applyIf(!sessionParameters.allowSyntheticJavaProperties) { filter { it.symbol !is KtSyntheticJavaPropertySymbol } }
        .applyIf(!sessionParameters.allowJavaGettersAndSetters) {
            filterOutJavaGettersAndSetters(type, visibilityChecker, scopeNameFilter, symbolFilter)
        }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KtNamedClassOrObjectSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.getDeclaredMemberScope().getConstructors() }.map { it.asSignature() }

    val nonExtensionsFromType = (callables + innerClassesConstructors).filterNonExtensions(visibilityChecker, symbolFilter)

    val scopeIndex = indexInTower ?: CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return nonExtensionsFromType
        .map { KtCallableSignatureWithContainingScopeKind(it, KtScopeKind.TypeScope(scopeIndex)) }
        .applyIf(sessionParameters.excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.signature.symbol) } }
}

context(KtAnalysisSession)
private val KtSyntheticJavaPropertySymbol.getterAndSetter: List<KtCallableSymbol>
    get() = listOfNotNull(javaGetterSymbol, javaSetterSymbol)

context(KtAnalysisSession)
private fun Sequence<KtCallableSignature<*>>.filterOutJavaGettersAndSetters(
    type: KtType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KtCallableSymbol) -> Boolean
): Sequence<KtCallableSignature<*>> {
    val syntheticJavaPropertiesTypeScope = type.getSyntheticJavaPropertiesScope() ?: return this
    val syntheticProperties = syntheticJavaPropertiesTypeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .filterNonExtensions(visibilityChecker, symbolFilter)
        .filterIsInstance<KtCallableSignature<KtSyntheticJavaPropertySymbol>>()
    val javaGetterAndSetterSymbols = syntheticProperties.flatMapTo(mutableSetOf()) { it.symbol.getterAndSetter }

    return filter { it.symbol !in javaGetterAndSetterSymbols }
}

/**
 * Returns non-extensions from [KtScope]. Resulting callables do not include synthetic Java properties and constructors of inner classes.
 * To get them use [collectNonExtensionsForType].
 */
context(KtAnalysisSession)
internal fun collectNonExtensionsFromScope(
    scope: KtScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: KtScopeNameFilter,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KtCallableSymbol) -> Boolean,
): Sequence<KtCallableSignature<*>> = scope.getCallableSymbols(scopeNameFilter.getAndSetAware())
    .map { it.asSignature() }
    .filterNonExtensions(visibilityChecker, symbolFilter)
    .applyIf(sessionParameters.excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.symbol) } }

context(KtAnalysisSession)
private fun Sequence<KtCallableSignature<*>>.filterNonExtensions(
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KtCallableSymbol) -> Boolean,
): Sequence<KtCallableSignature<*>> = this
    .filterNot { it.symbol.isExtension }
    .filter { symbolFilter(it.symbol) }
    .filter { visibilityChecker.isVisible(it.symbol) }

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

context(KtAnalysisSession)
private fun isEnumEntriesProperty(symbol: KtCallableSymbol): Boolean {
    return symbol is KtPropertySymbol &&
            symbol.isStatic &&
            symbol.callableIdIfNonLocal?.callableName == StandardNames.ENUM_ENTRIES &&
            (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.ENUM_CLASS
}