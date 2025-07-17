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
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters.Companion.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile

object KtOutsideTowerScopeKinds {

    // please do not expose
    private const val INDEX_IN_TOWER: Int = -1

    val LocalScope: KaScopeKinds.LocalScope = KaScopeKinds.LocalScope(INDEX_IN_TOWER)
    val TypeScope: KaScopeKinds.TypeScope = KaScopeKinds.TypeScope(INDEX_IN_TOWER)
    val PackageMemberScope: KaScopeKinds.PackageMemberScope = KaScopeKinds.PackageMemberScope(INDEX_IN_TOWER)
    val StaticMemberScope: KaScopeKinds.StaticMemberScope = KaScopeKinds.StaticMemberScope(INDEX_IN_TOWER)
}

context(KaSession)
internal fun KotlinRawPositionContext.resolveReceiverToSymbols(): Sequence<KaSymbol> =
    when (this) {
        is KotlinNameReferencePositionContext -> resolveReceiverToSymbols()
        else -> sequenceOf(rootPackageSymbol)
    }

context(KaSession)
internal fun KotlinNameReferencePositionContext.resolveReceiverToSymbols(): Sequence<KaSymbol> =
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
    parameters: KotlinFirCompletionParameters,
    positionContext: KotlinNameReferencePositionContext,
    scopeContext: KaScopeContext,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KtCallableSignatureWithContainingScopeKind> = sequence {
    val indexedImplicitReceivers = scopeContext.implicitReceivers.associateBy { it.scopeIndexInTower }
    val scopes = scopeContext.scopes
        .filter { it.kind is KaScopeKind.LocalScope || it.kind is KaScopeKind.TypeScope }

    for (scopeWithKind in scopes) {
        val kind = scopeWithKind.kind
        val isImplicitReceiverScope = kind is KaScopeKind.TypeScope && kind.indexInTower in indexedImplicitReceivers

        val nonExtensions = if (isImplicitReceiverScope) {
            val implicitReceiver = indexedImplicitReceivers.getValue(kind.indexInTower)
            val scopeKind = KaScopeKinds.TypeScope(implicitReceiver.scopeIndexInTower)

            collectNonExtensionsForType(
                parameters = parameters,
                positionContext = positionContext,
                receiverType = implicitReceiver.type,
                visibilityChecker = visibilityChecker,
                scopeNameFilter = scopeNameFilter,
                symbolFilter = symbolFilter,
            ).map {
                KtCallableSignatureWithContainingScopeKind(it, scopeKind)
            }
        } else {
            collectNonExtensionsFromScope(
                parameters = parameters,
                positionContext = positionContext,
                scope = scopeWithKind.scope,
                visibilityChecker = visibilityChecker,
                scopeNameFilter = scopeNameFilter,
                symbolFilter = symbolFilter,
            ).map {
                KtCallableSignatureWithContainingScopeKind(it, scopeWithKind.kind)
            }
        }
        yieldAll(nonExtensions)
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun collectNonExtensionsForType(
    parameters: KotlinFirCompletionParameters,
    positionContext: KotlinNameReferencePositionContext,
    receiverType: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean = { true },
): Sequence<KaCallableSignature<*>> {
    if (receiverType is KaErrorType) return emptySequence()
    val typeScope = receiverType.scope ?: return emptySequence()

    val languageVersionSettings = parameters.languageVersionSettings // todo is it possible to reuse WeighingContext?
    val excludeSyntheticJavaProperties = languageVersionSettings.excludeSyntheticJavaProperties(positionContext)

    val callables = typeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .applyIf(excludeSyntheticJavaProperties) {
            filterNot { it.symbol is KaSyntheticJavaPropertySymbol }
        }.applyIf(!excludeSyntheticJavaProperties && parameters.invocationCount <= 1) {
            filterOutJavaGettersAndSetters(
                positionContext = positionContext,
                type = receiverType,
                visibilityChecker = visibilityChecker,
                scopeNameFilter = scopeNameFilter,
                symbolFilter = symbolFilter,
            )
        }

    val innerClasses = typeScope.getClassifierSymbols(scopeNameFilter).filterIsInstance<KaNamedClassSymbol>().filter { it.isInner }
    val innerClassesConstructors = innerClasses.flatMap { it.declaredMemberScope.constructors }.map { it.asSignature() }

    val nonExtensionsFromType = (callables + innerClassesConstructors)
        .filterNonExtensions(positionContext, visibilityChecker, symbolFilter)

    return nonExtensionsFromType.applyIf(languageVersionSettings.excludeEnumEntries) {
        filterNot { isEnumEntriesProperty(it.symbol) }
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun Sequence<KaCallableSignature<*>>.filterOutJavaGettersAndSetters(
    positionContext: KotlinNameReferencePositionContext,
    type: KaType,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean
): Sequence<KaCallableSignature<*>> {
    val syntheticJavaPropertiesTypeScope = type.syntheticJavaPropertiesScope ?: return this
    // non-Unit setters are not filtered out because they are likely to be used in a call chain
    val javaGetterAndUnitSetterSymbols = syntheticJavaPropertiesTypeScope.getCallableSignatures(scopeNameFilter.getAndSetAware())
        .filterNonExtensions(positionContext, visibilityChecker, symbolFilter)
        .filterIsInstance<KaCallableSignature<KaSyntheticJavaPropertySymbol>>()
        .map { it.symbol }
        .flatMapTo(mutableSetOf()) { symbol ->
            listOfNotNull(
                symbol.javaGetterSymbol,
                symbol.javaSetterSymbol?.takeIf { it.returnType.isUnitType })
        }

    return filterNot { it.symbol in javaGetterAndUnitSetterSymbols }
}

/**
 * Returns non-extensions from [KtScope]. Resulting callables do not include synthetic Java properties and constructors of inner classes.
 * To get them use [collectNonExtensionsForType].
 */
context(KaSession)
@OptIn(KaExperimentalApi::class)
internal fun collectNonExtensionsFromScope(
    parameters: KotlinFirCompletionParameters,
    positionContext: KotlinNameReferencePositionContext,
    scope: KaScope,
    visibilityChecker: CompletionVisibilityChecker,
    scopeNameFilter: (Name) -> Boolean,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> = scope.callables(scopeNameFilter.getAndSetAware())
    .map { it.asSignature() }
    .filterNonExtensions(positionContext, visibilityChecker, symbolFilter)
    .applyIf(parameters.languageVersionSettings.excludeEnumEntries) {
        filterNot { isEnumEntriesProperty(it.symbol) }
    }

context(KaSession)
private fun Sequence<KaCallableSignature<*>>.filterNonExtensions(
    positionContext: KotlinNameReferencePositionContext,
    visibilityChecker: CompletionVisibilityChecker,
    symbolFilter: (KaCallableSymbol) -> Boolean,
): Sequence<KaCallableSignature<*>> = this
    .filterNot { it.symbol.isExtension }
    .filter { symbolFilter(it.symbol) }
    .filter { visibilityChecker.isVisible(it.symbol, positionContext) }

/**
 * Returns a filter aware of prefixes. For example, a variable with the name `prop` satisfies the filter for all the following prefixes:
 * "p", "getP", "setP"
 */
private fun ((Name) -> Boolean).getAndSetAware(): (Name) -> Boolean = { propertyName ->
    val identifier = propertyName.identifierOrNullIfSpecial
    listOfNotNull(
        propertyName,
        identifier?.let { Name.identifier(JvmAbi.getterName(it)) },
        identifier?.let { Name.identifier(JvmAbi.setterName(it)) },
    ).any(this)
}

context(KaSession)
private fun isEnumEntriesProperty(symbol: KaCallableSymbol): Boolean {
    return symbol is KaPropertySymbol &&
            symbol.isStatic &&
            symbol.callableId?.callableName == StandardNames.ENUM_ENTRIES &&
            (symbol.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.ENUM_CLASS
}

private inline val LanguageVersionSettings.excludeEnumEntries: Boolean
    get() = !supportsFeature(LanguageFeature.EnumEntries)

private fun LanguageVersionSettings.excludeSyntheticJavaProperties(
    positionContext: KotlinNameReferencePositionContext,
): Boolean = positionContext is KDocNameReferencePositionContext
        || positionContext is KotlinCallableReferencePositionContext && !supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)

/**
 * Checks if the scope contains an alias for the [symbol] and returns the name of the alias.
 */
context(KaSession)
internal fun KtFile.getAliasNameIfExists(symbol: KaSymbol): Name? {
    val fqName = symbol.getFqNameIfPackageOrNonLocal() ?: return null
    // TODO: It's possible to optimize this by using a map for the aliases if it turns out to be a bottleneck.
    return findAliasByFqName(fqName)?.name?.let { Name.identifier(it) }
}