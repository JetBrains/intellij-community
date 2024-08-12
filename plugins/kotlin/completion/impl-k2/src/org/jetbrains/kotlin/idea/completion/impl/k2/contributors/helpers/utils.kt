// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

/**
 * Origin of [KaSymbol] used in completion suggestion
 */
internal sealed class CompletionSymbolOrigin {
    class Scope(val kind: KaScopeKind) : CompletionSymbolOrigin()

    object Index : CompletionSymbolOrigin()

    companion object {
        const val SCOPE_OUTSIDE_TOWER_INDEX: Int = -1
    }
}

context(KaSession)
internal fun KotlinRawPositionContext.resolveToSymbols(): Sequence<KaSymbol> =
    when (this) {
        is KotlinNameReferencePositionContext -> resolveToSymbols()
        else -> sequenceOf(rootPackageSymbol)
    }

context(KaSession)
internal fun KotlinNameReferencePositionContext.resolveToSymbols(): Sequence<KaSymbol> =
    when (val explicitReceiver = explicitReceiver) {
        null -> sequenceOf(rootPackageSymbol)
        else -> explicitReceiver.reference()
            ?.resolveToSymbols()
            ?.asSequence()
            ?: emptySequence()
    }

internal fun createStarTypeArgumentsList(typeArgumentsCount: Int): String =
    if (typeArgumentsCount > 0) {
        List(typeArgumentsCount) { "*" }.joinToString(prefix = "<", postfix = ">")
    } else {
        ""
    }

context(KaSession)
internal fun collectLocalAndMemberNonExtensionsFromScopeContext(
    scopeContext: KaScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = sequence {
    val indexedImplicitReceivers = scopeContext.implicitReceivers.associateBy { it.scopeIndexInTower }
    val scopes = scopeContext.scopes.filter { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }

    for (scopeWithKind in scopes) {
        val kind = scopeWithKind.kind
        val isImplicitReceiverScope = kind is KaScopeKind.TypeScope && kind.indexInTower in indexedImplicitReceivers

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

context(KaSession)
internal fun collectStaticAndTopLevelNonExtensionsFromScopeContext(
    scopeContext: KaScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = scopeContext.scopes.asSequence()
    .filterNot { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }
    .flatMap { scopeWithKind ->
        collectNonExtensionsFromScope(scopeWithKind.scope, visibilityChecker, scopeNameFilter, sessionParameters, symbolFilter)
            .map { KtCallableSignatureWithContainingScopeKind(it, scopeWithKind.kind) }
    }

/**
 * @param indexInTower index of implicit receiver's scope in scope tower if it is known, otherwise null.
 */
context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun collectNonExtensionsForType(
    type: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    sessionParameters: FirCompletionSessionParameters,
    indexInTower: Int? = null,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> {
    val typeScope = type.scope ?: return emptySequence()

    val callables = typeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .applyIf(!sessionParameters.allowSyntheticJavaProperties) { filter { it.symbol !is KaSyntheticJavaPropertySymbol } }
        .applyIf(!sessionParameters.allowJavaGettersAndSetters) {
            filterOutJavaGettersAndSetters(type, visibilityChecker, scopeNameFilter, symbolFilter)
        }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KaNamedClassSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.declaredMemberScope.constructors }.map { it.asSignature() }

    val nonExtensionsFromType = (callables + innerClassesConstructors).filterNonExtensions(visibilityChecker, symbolFilter)

    val scopeIndex = indexInTower ?: CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX

    return nonExtensionsFromType
        .map { KtCallableSignatureWithContainingScopeKind(it, KaScopeKinds.TypeScope(scopeIndex)) }
        .applyIf(sessionParameters.excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.signature.symbol) } }
}

context(KaSession)
private val KaSyntheticJavaPropertySymbol.getterAndUnitSetter: List<KaCallableSymbol>
    get() = listOfNotNull(javaGetterSymbol, javaSetterSymbol?.takeIf { it.returnType.isUnitType })

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun Sequence<KaCallableSignature<*>>.filterOutJavaGettersAndSetters(
    type: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean
): Sequence<KaCallableSignature<*>> {
    val syntheticJavaPropertiesTypeScope = type.syntheticJavaPropertiesScope ?: return this
    val syntheticProperties = syntheticJavaPropertiesTypeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .filterNonExtensions(visibilityChecker, symbolFilter)
        .filterIsInstance<KaCallableSignature<KaSyntheticJavaPropertySymbol>>()
    // non-Unit setters are not filtered out because they are likely to be used in a call chain
    val javaGetterAndUnitSetterSymbols = syntheticProperties.flatMapTo(mutableSetOf()) { it.symbol.getterAndUnitSetter }

    return filter { it.symbol !in javaGetterAndUnitSetterSymbols }
}

/**
 * Returns non-extensions from [KtScope]. Resulting callables do not include synthetic Java properties and constructors of inner classes.
 * To get them use [collectNonExtensionsForType].
 */
context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun collectNonExtensionsFromScope(
    scope: KaScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    sessionParameters: FirCompletionSessionParameters,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> = scope.callables(scopeNameFilter.getAndSetAware())
    .map { it.asSignature() }
    .filterNonExtensions(visibilityChecker, symbolFilter)
    .applyIf(sessionParameters.excludeEnumEntries) { filterNot { isEnumEntriesProperty(it.symbol) } }

context(KaSession)
private fun Sequence<KaCallableSignature<*>>.filterNonExtensions(
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> = this
    .filterNot { it.symbol.isExtension }
    .filter { symbolFilter(it.symbol) }
    .filter { visibilityChecker.isVisible(it.symbol) }

/**
 * Returns a filter aware of prefixes. For example, a variable with the name `prop` satisfies the filter for all the following prefixes:
 * "p", "getP", "setP"
 */
private fun ((Name) -> Boolean).getAndSetAware(): (Name) -> Boolean = { name ->
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

context(KaSession)
private fun isEnumEntriesProperty(symbol: KaCallableSymbol): Boolean {
    return symbol is KaPropertySymbol &&
            symbol.isStatic &&
            symbol.callableId?.callableName == StandardNames.ENUM_ENTRIES &&
            (symbol.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.ENUM_CLASS
}