// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.K2SoftDeprecationWeigher
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

internal class WeighingContext private constructor(
    override val token: KtLifetimeToken,
    val languageVersionSettings: LanguageVersionSettings,
    val explicitReceiver: KtExpression?,
    private val myExpectedType: KtType?,
    private val myImplicitReceivers: List<KtImplicitReceiver>,
    val importableFqNameClassifier: ImportableFqNameClassifier,
) : KtLifetimeOwner {
    val expectedType: KtType?
        get() = withValidityAssertion {
            myExpectedType
        }

    /** All implicit receivers in the current resolution context. The receiver declared in the inner most scope appears first. */
    val implicitReceiver: List<KtImplicitReceiver>
        get() = withValidityAssertion {
            myImplicitReceivers
        }

    fun withoutExpectedType(): WeighingContext = withValidityAssertion {
        WeighingContext(token, languageVersionSettings, explicitReceiver, null, myImplicitReceivers, importableFqNameClassifier)
    }

    companion object {
        context(KtAnalysisSession)
        fun createWeighingContext(
            receiver: KtExpression?,
            expectedType: KtType?,
            implicitReceivers: List<KtImplicitReceiver>,
            fakeCompletionFile: KtFile,
        ): WeighingContext {
            val defaultImportPaths = fakeCompletionFile.getDefaultImportPaths()
            val languageVersionSettings = fakeCompletionFile.languageVersionSettings
            return WeighingContext(
                token,
                languageVersionSettings,
                receiver,
                expectedType,
                implicitReceivers,
                ImportableFqNameClassifier(fakeCompletionFile) { defaultImportPaths.hasImport(it) })
        }

        context(KtAnalysisSession)
        fun createEmptyWeighingContext(
            fakeCompletionFile: KtFile
        ): WeighingContext = createWeighingContext(null, null, emptyList(), fakeCompletionFile)

        private fun KtFile.getDefaultImportPaths(): Set<ImportPath> {
            return this.platform.findAnalyzerServices(project)
                .getDefaultImports(languageVersionSettings, true).toSet()
        }

        private fun Set<ImportPath>.hasImport(name: FqName): Boolean {
            return ImportPath(name, false) in this || ImportPath(name.parent(), true) in this
        }
    }
}

internal object Weighers {
    context(KtAnalysisSession)
    fun applyWeighsToLookupElement(
        context: WeighingContext,
        lookupElement: LookupElement,
        symbol: KtSymbol?,
        scopeKind: KtScopeKind?,
        substitutor: KtSubstitutor = KtSubstitutor.Empty(token)
    ) {
        with(ExpectedTypeWeigher) { addWeight(context, lookupElement, symbol) }

        if (symbol == null) return

        with(DeprecatedWeigher) { addWeight(lookupElement, symbol) }
        with(PreferGetSetMethodsToPropertyWeigher) { addWeight(lookupElement, symbol) }
        with(NotImportedWeigher) { addWeight(context, lookupElement, symbol, availableWithoutImport = scopeKind != null) }
        with(KindWeigher) { addWeight(lookupElement, symbol) }
        with(ClassifierWeigher) { addWeight(lookupElement, symbol, scopeKind) }
        with(VariableOrFunctionWeigher) { addWeight(lookupElement, symbol) }
        with(K2SoftDeprecationWeigher) { addWeight(lookupElement, symbol, context.languageVersionSettings) }
    }

    context(KtAnalysisSession)
    fun applyWeighsToLookupElementForCallable(
        context: WeighingContext,
        lookupElement: LookupElement,
        signature: KtCallableSignature<*>,
        scopeKind: KtScopeKind?,
    ) {
        with(CallableWeigher) { addWeight(context, lookupElement, signature, scopeKind) }

        applyWeighsToLookupElement(context, lookupElement, signature.symbol, scopeKind)
    }

    fun addWeighersToCompletionSorter(sorter: CompletionSorter): CompletionSorter =
        sorter
            .weighBefore(
                PlatformWeighersIds.STATS,
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
            .weighBefore(ExpectedTypeWeigher.WEIGHER_ID, CompletionContributorGroupWeigher.Weigher)
            .weighBefore(
                PlatformWeighersIds.PREFIX,
                K2SoftDeprecationWeigher.Weigher,
                VariableOrParameterNameWithTypeWeigher.Weigher
            )
            .weighAfter(PlatformWeighersIds.PROXIMITY, ByNameAlphabeticalWeigher.Weigher)

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
