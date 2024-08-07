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
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectCallableOptions
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
object KotlinFirLookupElementFactory {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookupElement(
        symbol: KaNamedSymbol,
        importStrategyDetector: ImportStrategyDetector,
    ): LookupElement = when (symbol) {
        is KaCallableSymbol -> createCallableLookupElement(
            symbol.name,
            symbol.asSignature(),
            detectCallableOptions(symbol, importStrategyDetector),
            expectedType = null,
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
    ): LookupElementBuilder {
        return when (signature) {
            is KaFunctionSignature<*> -> FunctionLookupElementFactory.createLookup(name, signature, options, expectedType)
            is KaVariableSignature<*> -> VariableLookupElementFactory.createLookup(signature, options)
        }
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

    context(KaSession)
    fun createLookupElementForClassLikeSymbol(
        symbol: KaClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElement? {
        if (symbol !is KaNamedSymbol) return null
        return ClassLookupElementFactory.createLookup(symbol, importingStrategy)
    }
}


