// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.BracketOperatorInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectCallableOptions
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
object KotlinFirLookupElementFactory {
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createConstructorCallLookupElement(
        containingSymbol: KaNamedClassSymbol,
        visibleConstructorSymbols: List<KaConstructorSymbol>,
        inputTypeArgumentsAreRequired: Boolean,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
        aliasName: Name? = null,
    ): LookupElementBuilder? {
        if (visibleConstructorSymbols.isEmpty()) return null
        return ClassLookupElementFactory.createConstructorLookup(
            containingSymbol = containingSymbol,
            constructorSymbols = visibleConstructorSymbols,
            inputTypeArgumentsAreRequired = inputTypeArgumentsAreRequired,
            importingStrategy = importingStrategy,
            aliasName = aliasName
        )
    }

    context(_: KaSession)
    fun createClassifierLookupElement(
        symbol: KaClassifierSymbol,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
        aliasName: Name? = null,
    ): LookupElementBuilder? = when (symbol) {
        is KaClassLikeSymbol ->
            if (symbol is KaNamedSymbol) ClassLookupElementFactory.createLookup(symbol, importingStrategy, aliasName)
            else null

        is KaTypeParameterSymbol -> TypeParameterLookupElementFactory.createLookup(symbol)
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookupElement(
        symbol: KaNamedSymbol,
        importStrategyDetector: ImportStrategyDetector,
    ): LookupElement = when (symbol) {
        is KaCallableSymbol -> createCallableLookupElement(
            name = symbol.name,
            signature = symbol.asSignature(),
            options = detectCallableOptions(symbol, importStrategyDetector),
        )

        is KaClassLikeSymbol -> ClassLookupElementFactory
            .createLookup(symbol, importStrategyDetector.detectImportStrategyForClassifierSymbol(symbol))

        is KaTypeParameterSymbol -> TypeParameterLookupElementFactory.createLookup(symbol)
        else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
    }

    context(_: KaSession)
    fun createCallableLookupElement(
        name: Name,
        signature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        expectedType: KaType? = null,
        aliasName: Name? = null,
    ): LookupElementBuilder = when (signature) {
        is KaFunctionSignature<*> -> FunctionLookupElementFactory.createLookup(name, signature, options, expectedType, aliasName)
        is KaVariableSignature<*> -> VariableLookupElementFactory.createLookup(signature, options, aliasName)
    }

    context(_: KaSession)
    fun createBracketOperatorLookupElement(
        operatorName: Name,
        signature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        expectedType: KaType? = null,
    ): LookupElementBuilder {
        require(operatorName.identifier.length == 2) { "Bracket operator name '$operatorName' should consist of 2 characters (the brackets)" }
        val indexingLookupElement = createCallableLookupElement(operatorName, signature, options, expectedType)
            .withInsertHandler(BracketOperatorInsertionHandler)
        indexingLookupElement.priority = ItemPriority.BRACKET_OPERATOR
        return indexingLookupElement
    }

    context(_: KaSession)
    fun createAnonymousObjectLookupElement(
        classSymbol: KaClassSymbol,
        typeArguments: List<KaTypeProjection>?,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        return ClassLookupElementFactory.createAnonymousObjectLookup(
            symbol = classSymbol,
            typeArguments = typeArguments,
            importingStrategy = importingStrategy,
            aliasName = aliasName
        )
    }

    fun createPackagePartLookupElement(packagePartFqName: FqName): LookupElement =
        PackagePartLookupElementFactory.createLookup(packagePartFqName)

    context(_: KaSession)
    fun createNamedArgumentLookupElement(name: Name, types: List<IndexedValue<KaType>>): LookupElement =
        NamedArgumentLookupElementFactory.createLookup(name, types)

    fun createNamedArgumentWithValueLookupElement(name: Name, value: String, index: Int): LookupElement =
        NamedArgumentLookupElementFactory.createLookup(name, value, index)

    context(_: KaSession)
    fun createTypeLookupElement(type: KaType): LookupElement? =
        TypeLookupElementFactory.createLookup(type)

    context(_: KaSession)
    fun createTypeLookupElement(classSymbol: KaClassifierSymbol): LookupElement? =
        TypeLookupElementFactory.createLookup(classSymbol)
}
