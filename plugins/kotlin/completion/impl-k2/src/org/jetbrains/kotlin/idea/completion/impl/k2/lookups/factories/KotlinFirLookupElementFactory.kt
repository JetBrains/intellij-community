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
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.TypeLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.detectCallableOptions
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
class KotlinFirLookupElementFactory {
    private val classLookupElementFactory = ClassLookupElementFactory()
    private val variableLookupElementFactory = VariableLookupElementFactory()
    private val functionLookupElementFactory = FunctionLookupElementFactory()
    private val typeParameterLookupElementFactory = TypeParameterLookupElementFactory()
    private val packagePartLookupElementFactory = PackagePartLookupElementFactory()
    private val namedArgumentLookupElementFactory = NamedArgumentLookupElementFactory()
    private val typeLookupElementFactory = TypeLookupElementFactory()

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun createLookupElement(
        symbol: KaNamedSymbol,
        importStrategyDetector: ImportStrategyDetector,
        importingStrategy: ImportStrategy? = null,
        expectedType: KaType? = null,
    ): LookupElement {
        return when (symbol) {
            is KaCallableSymbol -> createCallableLookupElement(
                symbol.name,
                symbol.asSignature(),
                detectCallableOptions(symbol, importStrategyDetector),
                expectedType,
            )

            is KaClassLikeSymbol -> classLookupElementFactory
                .createLookup(symbol, importingStrategy ?: importStrategyDetector.detectImportStrategyForClassifierSymbol(symbol))

            is KaTypeParameterSymbol -> typeParameterLookupElementFactory.createLookup(symbol)
            else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
        }
    }

    context(KaSession)
    fun createCallableLookupElement(
        name: Name,
        signature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        expectedType: KaType? = null,
    ): LookupElementBuilder {
        return when (signature) {
            is KaFunctionSignature<*> -> functionLookupElementFactory.createLookup(name, signature, options, expectedType)
            is KaVariableSignature<*> -> variableLookupElementFactory.createLookup(signature, options)
        }
    }

    fun createPackagePartLookupElement(packagePartFqName: FqName): LookupElement =
        packagePartLookupElementFactory.createPackagePartLookupElement(packagePartFqName)

    context(KaSession)
    fun createNamedArgumentLookupElement(name: Name, types: List<KaType>): LookupElement =
        namedArgumentLookupElementFactory.createNamedArgumentLookup(name, types)

    fun createNamedArgumentWithValueLookupElement(name: Name, value: String): LookupElement =
        namedArgumentLookupElementFactory.createNamedArgumentWithValueLookup(name, value)

    context(KaSession)
    fun createTypeLookupElement(type: KaType): LookupElement? =
        typeLookupElementFactory.createLookup(type)

    context(KaSession)
    fun createTypeLookupElement(classSymbol: KaClassifierSymbol): LookupElement? =
        typeLookupElementFactory.createLookup(classSymbol)

    context(KaSession)
    fun createLookupElementForClassLikeSymbol(
        symbol: KaClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElement? {
        if (symbol !is KaNamedSymbol) return null
        return classLookupElementFactory.createLookup(symbol, importingStrategy)
    }
}


