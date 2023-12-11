// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfType
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.utils.fqname.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.K2SoftDeprecationWeigher
import org.jetbrains.kotlin.idea.completion.isPositionSuitableForNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSuperReceiverNameReferencePositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.ImportPath

internal class WeighingContext private constructor(
    override val token: KtLifetimeToken,
    val languageVersionSettings: LanguageVersionSettings,
    val explicitReceiver: KtElement?,
    private val positionInFakeCompletionFile: PsiElement,
    private val myExpectedType: KtType?,
    private val myImplicitReceivers: List<KtImplicitReceiver>,
    val contextualSymbolsCache: ContextualSymbolsCache,
    val importableFqNameClassifier: ImportableFqNameClassifier,
    private val mySymbolsToSkip: Set<KtSymbol>,
) : KtLifetimeOwner {
    /**
     * Cache for contextual symbols, i.e. symbols which are overridden by callables containing current position.
     */
    class ContextualSymbolsCache(private val symbolsContainingPosition: Map<Name, List<KtCallableSymbol>>) {
        private val contextualOverriddenSymbols: MutableMap<Name, Set<KtCallableSymbol>> = mutableMapOf()
        context(KtAnalysisSession)
        fun symbolIsPresentInContext(symbol: KtCallableSymbol): Boolean = withValidityAssertion {
            if (symbol !is KtNamedSymbol) return false

            val symbols = symbolsContainingPosition[symbol.name].orEmpty()
            if (symbol in symbols) return true

            val overriddenSymbols = contextualOverriddenSymbols.getOrPut(symbol.name) {
                symbols.flatMap { it.getAllOverriddenSymbols() }.toSet()
            }

            return symbol.unwrapFakeOverrides in overriddenSymbols
        }

        context(KtAnalysisSession)
        operator fun contains(name: Name): Boolean = withValidityAssertion { name in symbolsContainingPosition }
    }

    val expectedType: KtType?
        get() = withValidityAssertion {
            myExpectedType
        }

    /** All implicit receivers in the current resolution context. The receiver declared in the inner most scope appears first. */
    val implicitReceiver: List<KtImplicitReceiver>
        get() = withValidityAssertion {
            myImplicitReceivers
        }

    /**
     * Symbols that are very unlikely to be completed. They will appear on low positions in completion.
     */
    val symbolsToSkip: Set<KtSymbol>
        get() = withValidityAssertion {
            mySymbolsToSkip
        }

    val isPositionSuitableForNull: Boolean = isPositionSuitableForNull(positionInFakeCompletionFile)

    fun withoutExpectedType(): WeighingContext = withValidityAssertion {
        WeighingContext(
            token,
            languageVersionSettings,
            explicitReceiver,
            positionInFakeCompletionFile,
            myExpectedType = null,
            myImplicitReceivers,
            contextualSymbolsCache,
            importableFqNameClassifier,
            mySymbolsToSkip,
        )
    }

    companion object {
        context(KtAnalysisSession)
        fun createWeighingContext(
            receiver: KtElement?,
            expectedType: KtType?,
            implicitReceivers: List<KtImplicitReceiver>,
            positionInFakeCompletionFile: PsiElement,
            symbolsToSkip: Set<KtSymbol> = emptySet(),
        ): WeighingContext {
            val fakeCompletionFile = positionInFakeCompletionFile.containingFile as KtFile
            val defaultImportPaths = fakeCompletionFile.getDefaultImportPaths()
            val languageVersionSettings = fakeCompletionFile.languageVersionSettings
            return WeighingContext(
                token,
                languageVersionSettings,
                receiver,
                positionInFakeCompletionFile,
                expectedType,
                implicitReceivers,
                positionInFakeCompletionFile.getContextualSymbolsCache(),
                ImportableFqNameClassifier(fakeCompletionFile) { defaultImportPaths.hasImport(it) },
                symbolsToSkip,
            )
        }

        context(KtAnalysisSession)
        fun createEmptyWeighingContext(
            positionInFakeCompletionFile: PsiElement,
        ): WeighingContext = createWeighingContext(
            receiver = null,
            expectedType = null,
            implicitReceivers = emptyList(),
            positionInFakeCompletionFile
        )

        private fun KtFile.getDefaultImportPaths(): Set<ImportPath> {
            return this.platform.findAnalyzerServices(project)
                .getDefaultImports(languageVersionSettings, true).toSet()
        }

        private fun Set<ImportPath>.hasImport(name: FqName): Boolean {
            return ImportPath(name, false) in this || ImportPath(name.parent(), true) in this
        }

        context(KtAnalysisSession)
        private fun PsiElement.getContextualSymbolsCache(): ContextualSymbolsCache {
            // don't collect contextual symbols for position without reference
            if ((parent as? KtSimpleNameExpression)?.mainReference == null) return ContextualSymbolsCache(emptyMap())

            val containingCallableDeclarations = parentsOfType<KtCallableDeclaration>().filter { it !is KtParameter }
            val containingCallableSymbols = containingCallableDeclarations.map { it.getSymbolOfType<KtCallableSymbol>() }

            return containingCallableSymbols
                .filter { it is KtNamedSymbol }
                .groupBy { (it as KtNamedSymbol).name }
                .let { ContextualSymbolsCache(it) }
        }
    }
}

internal object Weighers {
    context(KtAnalysisSession)
    fun applyWeighsToLookupElement(
        context: WeighingContext,
        lookupElement: LookupElement,
        symbolWithOrigin: KtSymbolWithOrigin?,
    ) {
        ExpectedTypeWeigher.addWeight(context, lookupElement, symbolWithOrigin?.symbol)
        KindWeigher.addWeight(lookupElement, symbolWithOrigin?.symbol, context)

        if (symbolWithOrigin == null) return
        val symbol = symbolWithOrigin.symbol

        val availableWithoutImport = symbolWithOrigin.origin is CompletionSymbolOrigin.Scope

        DeprecatedWeigher.addWeight(lookupElement, symbol)
        PreferGetSetMethodsToPropertyWeigher.addWeight(lookupElement, symbol)
        NotImportedWeigher.addWeight(context, lookupElement, symbol, availableWithoutImport)
        ClassifierWeigher.addWeight(lookupElement, symbol, symbolWithOrigin.origin)
        VariableOrFunctionWeigher.addWeight(lookupElement, symbol)
        K2SoftDeprecationWeigher.addWeight(lookupElement, symbol, context.languageVersionSettings)

        if (symbol !is KtCallableSymbol) return

        PreferContextualCallablesWeigher.addWeight(lookupElement, symbol, context.contextualSymbolsCache)
        PreferFewerParametersWeigher. addWeight(lookupElement, symbol)
    }

    context(KtAnalysisSession)
    fun applyWeighsToLookupElementForCallable(
        context: WeighingContext,
        lookupElement: LookupElement,
        signature: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
    ) {
        CallableWeigher.addWeight(context, lookupElement, signature, symbolOrigin)

        applyWeighsToLookupElement(context, lookupElement, KtSymbolWithOrigin(signature.symbol, symbolOrigin))
    }

    fun addWeighersToCompletionSorter(sorter: CompletionSorter, positionContext: KotlinRawPositionContext): CompletionSorter =
        sorter
            .weighBefore(
                PlatformWeighersIds.STATS,
                CompletionContributorGroupWeigher.Weigher,
                ExpectedTypeWeigher.Weigher,
                DeprecatedWeigher.Weigher,
                PriorityWeigher.Weigher,
                PreferGetSetMethodsToPropertyWeigher.Weigher,
                NotImportedWeigher.Weigher,
                KindWeigher.Weigher,
                CallableWeigher.Weigher,
                ClassifierWeigher.Weigher,
            )
            .weighAfter(
                PlatformWeighersIds.STATS,
                VariableOrFunctionWeigher.Weigher
            )
            .weighBefore(
                PlatformWeighersIds.PREFIX,
                K2SoftDeprecationWeigher.Weigher,
                VariableOrParameterNameWithTypeWeigher.Weigher
            )
            .weighAfter(
                PlatformWeighersIds.PROXIMITY,
                ByNameAlphabeticalWeigher.Weigher,
                PreferFewerParametersWeigher.Weigher,
            )
            .weighBefore(getBeforeIdForContextualCallablesWeigher(positionContext), PreferContextualCallablesWeigher.Weigher)

    private fun getBeforeIdForContextualCallablesWeigher(positionContext: KotlinRawPositionContext): String =
        when (positionContext) {
            // prefer contextual callable when completing reference after "super."
            is KotlinSuperReceiverNameReferencePositionContext -> ExpectedTypeWeigher.WEIGHER_ID
            else -> PlatformWeighersIds.PROXIMITY
        }

    private object PlatformWeighersIds {
        const val PREFIX = "prefix"
        const val STATS = "stats"
        const val PROXIMITY = "proximity"
    }
}

internal data class CompoundWeight2<W1 : Comparable<*>, W2 : Comparable<*>>(
    val weight1: W1,
    val weight2: W2
) : Comparable<CompoundWeight2<W1, W2>> {
    override fun compareTo(other: CompoundWeight2<W1, W2>): Int {
        return compareValuesBy(this, other, { it.weight1 }, { it.weight2 })
    }
}

internal data class CompoundWeight3<W1 : Comparable<*>, W2 : Comparable<*>, W3 : Comparable<*>>(
    val weight1: W1,
    val weight2: W2,
    val weight3: W3
) : Comparable<CompoundWeight3<W1, W2, W3>> {
    override fun compareTo(other: CompoundWeight3<W1, W2, W3>): Int {
        return compareValuesBy(this, other, { it.weight1 }, { it.weight2 }, { it.weight3 })
    }
}
