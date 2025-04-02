// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.resolution.KaAnnotationCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getDefaultImportPaths
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters.Companion.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters.Companion.useSiteModule
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.K2SoftDeprecationWeigher
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaParameterNameWeigher
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaWeigher
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.PreferKotlinClassesWeigher
import org.jetbrains.kotlin.idea.completion.isPositionInsideImportOrPackageDirective
import org.jetbrains.kotlin.idea.completion.isPositionSuitableForNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath

internal class WeighingContext private constructor(
    override val token: KaLifetimeToken,
    val languageVersionSettings: LanguageVersionSettings,
    private val positionInFakeCompletionFile: PsiElement,
    private val myScopeContext: KaScopeContext?,
    private val myExpectedType: KaType?,
    private val myActualReceiverTypes: List<List<KaType>>,
    val contextualSymbolsCache: ContextualSymbolsCache,
    val importableFqNameClassifier: ImportableFqNameClassifier,
    private val mySymbolsToSkip: Set<KaSymbol>,
) : KaLifetimeOwner {

    /**
     * Cache for contextual symbols, i.e. symbols which are overridden by callables containing current position.
     */
    class ContextualSymbolsCache(private val symbolsContainingPosition: Map<Name, List<KaCallableSymbol>>) {
        private val contextualOverriddenSymbols: MutableMap<Name, Set<KaCallableSymbol>> = mutableMapOf()
        context(KaSession)
        fun symbolIsPresentInContext(symbol: KaCallableSymbol): Boolean = withValidityAssertion {
            if (symbol !is KaNamedSymbol) return false

            val symbols = symbolsContainingPosition[symbol.name].orEmpty()
            if (symbol in symbols) return true

            val overriddenSymbols = contextualOverriddenSymbols.getOrPut(symbol.name) {
                symbols.flatMap { it.allOverriddenSymbols }.toSet()
            }

            return symbol.fakeOverrideOriginal in overriddenSymbols
        }

        context(KaSession)
        operator fun contains(name: Name): Boolean = withValidityAssertion { name in symbolsContainingPosition }
    }

    val scopeContext: KaScopeContext?
        get() = withValidityAssertion { myScopeContext }

    val expectedType: KaType?
        get() = withValidityAssertion {
            myExpectedType
        }

    val actualReceiverTypes: List<List<KaType>>
        get() = withValidityAssertion { myActualReceiverTypes }

    /**
     * Symbols that are very unlikely to be completed. They will appear on low positions in completion.
     */
    val symbolsToSkip: Set<KaSymbol>
        get() = withValidityAssertion {
            mySymbolsToSkip
        }

    val isPositionSuitableForNull: Boolean = isPositionSuitableForNull(positionInFakeCompletionFile)
    val isPositionInsideImportOrPackageDirective: Boolean = isPositionInsideImportOrPackageDirective(positionInFakeCompletionFile)

    companion object {

        context(KaSession)
        fun create(
            parameters: KotlinFirCompletionParameters,
            elementInCompletionFile: PsiElement,
            scopeContext: KaScopeContext? = null,
            expectedType: KaType? = null,
            actualReceiverTypes: List<List<KaType>> = emptyList(),
            symbolsToSkip: Set<KaSymbol> = emptySet(),
        ): WeighingContext {
            val completionFile = parameters.completionFile
            val defaultImportPaths = completionFile.getDefaultImportPaths(useSiteModule = parameters.useSiteModule).toSet()
            return WeighingContext(
                token = token,
                languageVersionSettings = parameters.languageVersionSettings,
                positionInFakeCompletionFile = elementInCompletionFile,
                myScopeContext = scopeContext,
                myExpectedType = expectedType,
                myActualReceiverTypes = actualReceiverTypes,
                contextualSymbolsCache = ContextualSymbolsCache(
                    getContextualSymbolsCache(
                        elementInCompletionFile = elementInCompletionFile,
                        originalFile = parameters.originalFile,
                    )
                ),
                importableFqNameClassifier = ImportableFqNameClassifier(completionFile) { defaultImportPaths.hasImport(it) },
                mySymbolsToSkip = symbolsToSkip,
            )
        }

        context(KaSession)
        fun create(
            parameters: KotlinFirCompletionParameters,
            positionContext: KotlinNameReferencePositionContext,
        ): WeighingContext {
            val nameExpression = positionContext.nameExpression
            val expectedType = when {
                // during the sorting of completion suggestions expected type from position and actual types of suggestions are compared;
                // see `org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher`;
                // currently in case of callable references actual types are calculated incorrectly, which is why we don't use information
                // about expected type at all
                // TODO: calculate actual types for callable references correctly and use information about expected type
                positionContext is KotlinCallableReferencePositionContext -> null
                nameExpression.expectedType != null -> nameExpression.expectedType
                nameExpression.parent is KtBinaryExpression -> getEqualityExpectedType(nameExpression)
                nameExpression.parent is KtCollectionLiteralExpression -> getAnnotationLiteralExpectedType(nameExpression)
                else -> null
            }

            val symbolToSkip = when (positionContext) {
                is KotlinWithSubjectEntryPositionContext -> (positionContext.subjectExpression as? KtSimpleNameExpression)
                    ?.mainReference
                    ?.resolveToSymbol()

                else -> null
            }

            val scopeContext: KaScopeContext = parameters.originalFile
                .scopeContext(nameExpression)

            fun implicitReceivers(): List<KaImplicitReceiver> = when (positionContext) {
                // Implicit receivers do not match for this position completion context.
                is KotlinSuperReceiverNameReferencePositionContext -> emptyList()
                else -> scopeContext.implicitReceivers
            }

            return create(
                parameters = parameters,
                elementInCompletionFile = positionContext.position,
                scopeContext = scopeContext,
                expectedType = expectedType,
                actualReceiverTypes = CallableMetadataProvider.calculateActualReceiverTypes(
                    explicitReceiver = positionContext.explicitReceiver,
                    implicitReceivers = ::implicitReceivers,
                ),
                symbolsToSkip = setOfNotNull(symbolToSkip),
            )
        }

        /**
         * Returns the expected type for elements within the collection literal
         * if [nameExpression] is within a collection literal.
         *
         * TODO: It seems like a bug in the analysis API that this is required: KT-76480
         */
        context(KaSession)
        private fun getAnnotationLiteralExpectedType(
            nameExpression: KtElement,
        ): KaType? {
            val collectionLiteralEntry = nameExpression.parent as? KtCollectionLiteralExpression ?: return null
            val annotationArgument = collectionLiteralEntry.parent as? KtValueArgument ?: return null
            val annotationEntry = annotationArgument.parentOfType<KtAnnotationEntry>() ?: return null
            val annotationCall = annotationEntry.resolveToCall()?.successfulCallOrNull<KaAnnotationCall>() ?: return null
            val callArgument = annotationCall.argumentMapping[collectionLiteralEntry] ?: return null
            return callArgument.symbol.returnType
        }

        context(KaSession)
        private fun getEqualityExpectedType(
            nameExpression: KtElement,
        ): KaType? {
            val binaryExpression = nameExpression.parent as? KtBinaryExpression
                ?: return null

            val isEqualityCheck = when (binaryExpression.operationToken) {
                KtTokens.EQEQ, KtTokens.EXCLEQ,
                KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ -> true

                else -> false
            }
            if (!isEqualityCheck) return null

            val left = binaryExpression.left
                ?: return null
            val right = binaryExpression.right
                ?: return null

            val expression = when (nameExpression) {
                left -> right
                right -> left
                else -> null
            }

            return expression?.expressionType
                ?.takeUnless { it is KaErrorType }
                ?.withNullability(newNullability = KaTypeNullability.NULLABLE)
        }

        private fun Set<ImportPath>.hasImport(name: FqName): Boolean {
            return ImportPath(name, false) in this || ImportPath(name.parent(), true) in this
        }

        context(KaSession)
        private fun getContextualSymbolsCache(
            elementInCompletionFile: PsiElement,
            originalFile: KtFile,
        ): Map<Name, List<KaCallableSymbol>> {
            if (elementInCompletionFile.parent !is KtSimpleNameExpression) {
                return emptyMap()
            }

            return elementInCompletionFile.parentsOfType<KtCallableDeclaration>()
                .filterNot { it is KtParameter }
                .map { getOriginalDeclarationOrSelf(it, originalFile) }
                .map { it.symbol }
                .filterIsInstance<KaCallableSymbol>()
                .filter { it is KaNamedSymbol }
                .groupBy { (it as KaNamedSymbol).name }
        }
    }
}

internal object Weighers {

    context(KaSession)
    fun <E : LookupElement> E.applyWeighs(
        context: WeighingContext,
        symbolWithOrigin: KtSymbolWithOrigin? = null,
    ): E = also { lookupElement -> // todo replace everything with apply
        ExpectedTypeWeigher.addWeight(context, lookupElement, symbolWithOrigin?.symbol)
        KindWeigher.addWeight(lookupElement, symbolWithOrigin?.symbol, context)

        if (symbolWithOrigin == null) return@also
        val symbol = symbolWithOrigin.symbol

        val availableWithoutImport = symbolWithOrigin.origin is CompletionSymbolOrigin.Scope

        DeprecatedWeigher.addWeight(lookupElement, symbol)
        PreferGetSetMethodsToPropertyWeigher.addWeight(lookupElement, symbol)
        NotImportedWeigher.addWeight(context, lookupElement, symbol, availableWithoutImport)
        ClassifierWeigher.addWeight(lookupElement, symbol, symbolWithOrigin.origin)
        VariableOrFunctionWeigher.addWeight(lookupElement, symbol)

        if (symbol !is KaCallableSymbol) return@also

        K2SoftDeprecationWeigher.addWeight(lookupElement, symbol, context.languageVersionSettings)

        PreferContextualCallablesWeigher.addWeight(lookupElement, symbol, context.contextualSymbolsCache)
        PreferFewerParametersWeigher. addWeight(lookupElement, symbol)
    }

    fun CompletionSorter.applyWeighers(positionContext: KotlinRawPositionContext): CompletionSorter =
        weighBefore(
            PlatformWeighersIds.STATS,
            TrailingLambdaParameterNameWeigher,
            CompletionContributorGroupWeigher.Weigher,
            ExpectedTypeWeigher.Weigher,
            DeprecatedWeigher.Weigher,
            PriorityWeigher.Weigher,
            PreferGetSetMethodsToPropertyWeigher.Weigher,
            NotImportedWeigher.Weigher,
            KindWeigher.Weigher,
            CallableWeigher.Weigher,
            ClassifierWeigher.Weigher,
        ).weighAfter(
            PlatformWeighersIds.STATS,
            VariableOrFunctionWeigher.Weigher,
        ).weighBefore(
            PlatformWeighersIds.PREFIX,
            K2SoftDeprecationWeigher.Weigher,
            VariableOrParameterNameWithTypeWeigher.Weigher,
        ).weighAfter(
            PlatformWeighersIds.PROXIMITY,
            ByNameAlphabeticalWeigher.Weigher,
            PreferKotlinClassesWeigher.Weigher,
            PreferFewerParametersWeigher.Weigher,
            TrailingLambdaWeigher,
        ).weighBefore(
            getBeforeIdForContextualCallablesWeigher(positionContext),
            PreferContextualCallablesWeigher.Weigher,
        )

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
