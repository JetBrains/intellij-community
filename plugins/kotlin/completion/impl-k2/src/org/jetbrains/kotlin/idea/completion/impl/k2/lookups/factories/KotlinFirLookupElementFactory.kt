// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
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

    context(KtAnalysisSession)
    fun createLookupElement(
        symbol: KtNamedSymbol,
        importStrategyDetector: ImportStrategyDetector,
        importingStrategy: ImportStrategy? = null,
        expectedType: KtType? = null,
    ): LookupElement {
        return when (symbol) {
            is KtCallableSymbol -> createCallableLookupElement(
                symbol.name,
                symbol.asSignature(),
                detectCallableOptions(symbol, importStrategyDetector),
                expectedType,
            )

            is KtClassLikeSymbol -> classLookupElementFactory
                .createLookup(symbol, importingStrategy ?: importStrategyDetector.detectImportStrategyForClassifierSymbol(symbol))

            is KtTypeParameterSymbol -> typeParameterLookupElementFactory.createLookup(symbol)
            else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
        }
    }

    context(KtAnalysisSession)
    fun createCallableLookupElement(
        name: Name,
        signature: KtCallableSignature<*>,
        options: CallableInsertionOptions,
        expectedType: KtType? = null,
    ): LookupElementBuilder {
        return when (signature) {
            is KtFunctionLikeSignature<*> -> functionLookupElementFactory.createLookup(name, signature, options, expectedType)
            is KtVariableLikeSignature<*> -> variableLookupElementFactory.createLookup(signature, options)
        }
    }

    fun createPackagePartLookupElement(packagePartFqName: FqName): LookupElement =
        packagePartLookupElementFactory.createPackagePartLookupElement(packagePartFqName)

    context(KtAnalysisSession)
    fun createNamedArgumentLookupElement(name: Name, types: List<KtType>): LookupElement =
        namedArgumentLookupElementFactory.createNamedArgumentLookup(name, types)

    fun createNamedArgumentWithValueLookupElement(name: Name, value: String): LookupElement =
        namedArgumentLookupElementFactory.createNamedArgumentWithValueLookup(name, value)

    context(KtAnalysisSession)
    fun createTypeLookupElement(type: KtType): LookupElement? =
        typeLookupElementFactory.createLookup(type)

    context(KtAnalysisSession)
    fun createTypeLookupElement(classSymbol: KtClassifierSymbol): LookupElement? =
        typeLookupElementFactory.createLookup(classSymbol)

    context(KtAnalysisSession)
    fun createLookupElementForClassLikeSymbol(
        symbol: KtClassLikeSymbol,
        importingStrategy: ImportStrategy,
    ): LookupElement? {
        if (symbol !is KtNamedSymbol) return null
        return classLookupElementFactory.createLookup(symbol, importingStrategy)
    }
}


