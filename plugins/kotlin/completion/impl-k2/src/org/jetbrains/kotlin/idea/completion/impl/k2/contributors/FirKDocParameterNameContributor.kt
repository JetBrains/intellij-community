// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.TypeParameterLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext

internal open class FirKDocParameterNameContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KDocNameReferencePositionContext>(parameters, sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KDocNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        if (positionContext.explicitReceiver != null) return

        val section = positionContext.nameExpression.getContainingSection()
        val alreadyDocumentedParameters = section.findTagsByName(PARAMETER_TAG_NAME).map { it.getSubjectName() }.toSet()

        val ownerDeclaration = positionContext.nameExpression.getContainingDoc().owner ?: return

        getParametersForKDoc(ownerDeclaration.symbol)
            .filter { (it as KaNamedSymbol).name.asString() !in alreadyDocumentedParameters }
            .flatMap { createLookupElements(it, weighingContext) }
            .forEach(sink::addElement)
    }

    context(KaSession)
    private fun createLookupElements(
        declarationSymbol: KaDeclarationSymbol,
        weighingContext: WeighingContext,
    ): Sequence<LookupElement> = when (declarationSymbol) {
        is KaTypeParameterSymbol -> TypeParameterLookupElementFactory.createLookup(declarationSymbol)
            .applyWeighs(
                context = weighingContext,
                symbolWithOrigin = KtSymbolWithOrigin(
                    _symbol = declarationSymbol,
                    scopeKind = KtOutsideTowerScopeKinds.LocalScope,
                ),
            ).let { sequenceOf(it) }

        is KaValueParameterSymbol -> createCallableLookupElements(
            context = weighingContext,
            signature = @OptIn(KaExperimentalApi::class) (declarationSymbol.asSignature()),
            options = CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
            scopeKind = KtOutsideTowerScopeKinds.LocalScope,
        )

        else -> emptySequence()
    }

    context(KaSession)
    private fun getParametersForKDoc(
        ownerDeclarationSymbol: KaDeclarationSymbol
    ): Sequence<KaDeclarationSymbol> = sequence {
        @OptIn(KaExperimentalApi::class)
        yieldAll(ownerDeclarationSymbol.typeParameters)

        val valueParameters = when (ownerDeclarationSymbol) {
            is KaFunctionSymbol -> ownerDeclarationSymbol.valueParameters

            is KaNamedClassSymbol -> {
                val primaryConstructor = ownerDeclarationSymbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary }
                primaryConstructor?.valueParameters.orEmpty()
            }

            else -> emptyList()
        }
        yieldAll(valueParameters)
    }

    companion object {
        private const val PARAMETER_TAG_NAME: String = "param"
    }
}