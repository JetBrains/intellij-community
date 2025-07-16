// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
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
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createConstructorCallLookupElement(
        containingSymbol: KaNamedClassSymbol,
        visibleConstructorSymbols: List<KaConstructorSymbol>,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
        aliasName: Name? = null,
    ): LookupElementBuilder? {
        if (visibleConstructorSymbols.isEmpty()) return null
        return ClassLookupElementFactory.createConstructorLookup(containingSymbol, visibleConstructorSymbols, importingStrategy, aliasName)
    }

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
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

    context(KaSession)
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

    fun createPackagePartLookupElement(packagePartFqName: FqName): LookupElement =
        PackagePartLookupElementFactory.createLookup(packagePartFqName)

    context(KaSession)
    fun createNamedArgumentLookupElement(name: Name, types: List<KaType>): LookupElement =
        NamedArgumentLookupElementFactory.createLookup(name, types)

    fun createNamedArgumentWithValueLookupElement(name: Name, value: String): LookupElement =
        NamedArgumentLookupElementFactory.createLookup(name, value)

    context(KaSession)
    fun createTypeLookupElement(type: KaType): LookupElement? =
        TypeLookupElementFactory.createLookup(type)

    context(KaSession)
    fun createTypeLookupElement(classSymbol: KaClassifierSymbol): LookupElement? =
        TypeLookupElementFactory.createLookup(classSymbol)
}
