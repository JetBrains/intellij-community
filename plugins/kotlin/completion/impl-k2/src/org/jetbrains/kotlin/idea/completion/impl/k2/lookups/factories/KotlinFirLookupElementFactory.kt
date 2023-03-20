// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
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

    fun KtAnalysisSession.createLookupElement(
        symbol: KtNamedSymbol,
        importStrategyDetector: ImportStrategyDetector,
        importingStrategy: ImportStrategy? = null,
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token),
        expectedType: KtType? = null,
    ): LookupElement {
        return when (symbol) {
            is KtCallableSymbol -> createCallableLookupElement(
                symbol.name,
                symbol,
                detectCallableOptions(symbol, importStrategyDetector),
                substitutor,
                expectedType,
            )

            is KtClassLikeSymbol -> with(classLookupElementFactory) { createLookup(symbol, importingStrategy ?: importStrategyDetector.detectImportStrategy(symbol)) }
            is KtTypeParameterSymbol -> with(typeParameterLookupElementFactory) { createLookup(symbol) }
            else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
        }
    }

    fun KtAnalysisSession.createCallableLookupElement(
        name: Name,
        symbol: KtCallableSymbol,
        options: CallableInsertionOptions,
        substitutor: KtSubstitutor,
        expectedType: KtType? = null,
    ): LookupElementBuilder {
        return when (symbol) {
            is KtFunctionLikeSymbol -> with(functionLookupElementFactory) { createLookup(name, symbol, options, substitutor, expectedType) }
            is KtVariableLikeSymbol -> with(variableLookupElementFactory) { createLookup(symbol, options, substitutor) }
            else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
        }
    }

    fun createPackagePartLookupElement(packagePartFqName: FqName): LookupElement =
        packagePartLookupElementFactory.createPackagePartLookupElement(packagePartFqName)

    fun KtAnalysisSession.createNamedArgumentLookupElement(name: Name, types: List<KtType>): LookupElement =
        with(namedArgumentLookupElementFactory) { createNamedArgumentLookup(name, types) }

    fun createNamedArgumentWithValueLookupElement(name: Name, value: String): LookupElement =
        namedArgumentLookupElementFactory.createNamedArgumentWithValueLookup(name, value)

    fun KtAnalysisSession.createTypeLookupElement(type: KtType): LookupElement? =
        with(typeLookupElementFactory) { createLookup(type) }

    fun KtAnalysisSession.createTypeLookupElement(classSymbol: KtClassifierSymbol): LookupElement? =
        with(typeLookupElementFactory) { createLookup(classSymbol) }

    fun KtAnalysisSession.createLookupElementForClassLikeSymbol(
        symbol: KtClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElement? {
        if (symbol !is KtNamedSymbol) return null
        return with(classLookupElementFactory) { createLookup(symbol, importingStrategy) }
    }
}


