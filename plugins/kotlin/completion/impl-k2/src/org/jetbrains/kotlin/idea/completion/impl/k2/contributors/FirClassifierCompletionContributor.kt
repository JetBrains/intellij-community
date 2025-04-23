// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.SuspendingLookupElementRenderer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaScopeWithKind
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiers
import org.jetbrains.kotlin.idea.completion.contributors.helpers.FirClassifierProvider.getAvailableClassifiersFromIndex
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.factories.shortenCommand
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render
import kotlin.reflect.KClass
import kotlin.sequences.emptySequence

internal open class FirClassifierCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinNameReferencePositionContext>(parameters, sink, priority) {

    private val psiFactory = KtPsiFactory(project)

    context(KaSession)
    protected open fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = true

    context(KaSession)
    protected open fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy =
        importStrategyDetector.detectImportStrategyForClassifierSymbol(classifierSymbol)

    context(KaSession)
    override fun complete(
        positionContext: KotlinNameReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        val defaultFactory = (psiFactory::createExpression).asFactory()
        val factory = when (positionContext) {
            is KotlinTypeNameReferencePositionContext -> ({ type: String -> psiFactory.createType(type) }).asFactory()
            else -> defaultFactory
        }

        when (val explicitReceiver = positionContext.explicitReceiver) {
            null -> completeWithoutReceiver(positionContext, factory, weighingContext)
                .forEach(sink::addElement)

            else -> {
                val reference = explicitReceiver.reference()
                    ?: return

                val symbols = reference.resolveToSymbols()
                if (symbols.isNotEmpty()) {
                    symbols.asSequence()
                        .mapNotNull { it.staticScope }
                        .flatMap { scopeWithKind ->
                            scopeWithKind.completeClassifiers(positionContext)
                                .mapNotNull { classifierSymbol ->
                                    createClassifierLookupElement(classifierSymbol, factory)?.applyWeighs(
                                        context = weighingContext,
                                        symbolWithOrigin = KtSymbolWithOrigin(
                                            classifierSymbol,
                                            CompletionSymbolOrigin.Scope(scopeWithKind.kind)
                                        )
                                    )
                                }
                        }.forEach(sink::addElement)
                } else {
                    runChainCompletion(positionContext, explicitReceiver) { receiverExpression,
                                                                            positionContext,
                                                                            importingStrategy ->
                        val selectorExpression = receiverExpression.selectorExpression
                            ?: return@runChainCompletion emptySequence()

                        val reference = receiverExpression.reference()
                            ?: return@runChainCompletion emptySequence()

                        // TODO val weighingContext = WeighingContext.create(parameters, positionContext)
                        reference.resolveToSymbols()
                            .asSequence()
                            .mapNotNull { it.staticScope }
                            .flatMap { it.completeClassifiers(positionContext) }
                            .mapNotNull { it ->
                                createClassifierLookupElement(
                                    classifierSymbol = it,
                                    factory = defaultFactory,
                                    importingStrategy = importingStrategy,
                                )
                            }.map { it.withPresentableText(selectorExpression.text + "." + it.lookupString) }
                    }
                }
            }
        }
    }

    context(KaSession)
    private fun KaScopeWithKind.completeClassifiers(
        positionContext: KotlinNameReferencePositionContext,
    ): Sequence<KaClassifierSymbol> = scope
        .classifiers(scopeNameFilter)
        .filter { filterClassifiers(it) }
        .filter { visibilityChecker.isVisible(it, positionContext) }

    context(KaSession)
    private fun completeWithoutReceiver(
        positionContext: KotlinNameReferencePositionContext,
        factory: MethodBasedElementFactory<*>,
        context: WeighingContext,
    ): Sequence<LookupElementBuilder> {
        val availableFromScope = mutableSetOf<KaClassifierSymbol>()
        val scopesToCheck = context.scopeContext
            .scopes
            .toMutableList()

        context.preferredSubtype?.symbol?.let { preferredSubtypeSymbol ->
            preferredSubtypeSymbol.staticScope?.let {
                if (!scopesToCheck.contains(it)) {
                    scopesToCheck.add(it)
                }
            }
            preferredSubtypeSymbol.containingSymbol?.staticScope?.let {
                if (!scopesToCheck.contains(it)) {
                    scopesToCheck.add(it)
                }
            }
        }

        val scopeClassifiers = scopesToCheck
            .asSequence()
            .flatMap { it.getAvailableClassifiers(positionContext, scopeNameFilter, visibilityChecker) }
            .filter { filterClassifiers(it.symbol) }
            .mapNotNull { symbolWithScopeKind ->
                val classifierSymbol = symbolWithScopeKind.symbol
                availableFromScope += classifierSymbol

                createClassifierLookupElement(
                    classifierSymbol = classifierSymbol,
                    factory = factory,
                    importingStrategy = getImportingStrategy(classifierSymbol),
                )?.applyWeighs(
                    context = context,
                    symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol, CompletionSymbolOrigin.Scope(symbolWithScopeKind.scopeKind)),
                )
            }

        val indexClassifiers = if (prefixMatcher.prefix.isNotEmpty()) {
            getAvailableClassifiersFromIndex(
                positionContext = positionContext,
                parameters = parameters,
                symbolProvider = symbolFromIndexProvider,
                scopeNameFilter = scopeNameFilter,
                visibilityChecker = visibilityChecker,
            ).filter { it !in availableFromScope && filterClassifiers(it) }
                .mapNotNull { classifierSymbol ->
                    createClassifierLookupElement(
                        classifierSymbol = classifierSymbol,
                        factory = factory,
                        importingStrategy = getImportingStrategy(classifierSymbol),
                    )?.applyWeighs(
                        context = context,
                        symbolWithOrigin = KtSymbolWithOrigin(classifierSymbol, CompletionSymbolOrigin.Index),
                    )
                }
        } else {
            emptySequence()
        }

        return scopeClassifiers +
                indexClassifiers
    }

    context(KaSession)
    private fun createClassifierLookupElement(
        classifierSymbol: KaClassifierSymbol,
        factory: MethodBasedElementFactory<*>,
        importingStrategy: ImportStrategy = ImportStrategy.DoNothing,
    ): LookupElementBuilder? {
        val builder = KotlinFirLookupElementFactory.createClassifierLookupElement(classifierSymbol, importingStrategy)
            ?: return null

        return when (importingStrategy) {
            is ImportStrategy.InsertFqNameAndShorten -> builder.withExpensiveRenderer(
                ClassifierLookupElementRenderer(parameters, importingStrategy, factory),
            )

            else -> builder
        }
    }
}

internal class FirAnnotationCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirClassifierCompletionContributor(parameters, sink, priority) {

    context(KaSession)
    override fun filterClassifiers(classifierSymbol: KaClassifierSymbol): Boolean = when (classifierSymbol) {
        is KaAnonymousObjectSymbol -> false
        is KaTypeParameterSymbol -> false
        is KaNamedClassSymbol -> when (classifierSymbol.classKind) {
            KaClassKind.ANNOTATION_CLASS -> true
            KaClassKind.ENUM_CLASS -> false
            KaClassKind.ANONYMOUS_OBJECT -> false
            KaClassKind.CLASS, KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.INTERFACE -> {
                classifierSymbol.staticDeclaredMemberScope.classifiers.any { filterClassifiers(it) }
            }
        }

        is KaTypeAliasSymbol -> {
            val expendedClass = (classifierSymbol.expandedType as? KaClassType)?.symbol
            expendedClass?.let { filterClassifiers(it) } == true
        }
    }
}

internal class FirClassifierReferenceCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int
) : FirClassifierCompletionContributor(parameters, sink, priority) {

    context(KaSession)
    override fun getImportingStrategy(classifierSymbol: KaClassifierSymbol): ImportStrategy = when (classifierSymbol) {
        is KaTypeParameterSymbol -> ImportStrategy.DoNothing
        is KaClassLikeSymbol -> {
            classifierSymbol.classId?.let { ImportStrategy.AddImport(it.asSingleFqName()) } ?: ImportStrategy.DoNothing
        }
    }
}

private class ClassifierLookupElementRenderer<R : KtElement>(
    private val position: SmartPsiElementPointer<*>,
    private val offset: Int,
    private val fqName: FqName,
    private val factory: MethodBasedElementFactory<R>,
) : SuspendingLookupElementRenderer<LookupElement>() {

    constructor(
        parameters: KotlinFirCompletionParameters,
        importingStrategy: ImportStrategy.InsertFqNameAndShorten,
        factory: MethodBasedElementFactory<R>,
    ) : this(
        position = parameters.position.createSmartPointer(),
        offset = parameters.offset,
        fqName = importingStrategy.fqName,
        factory = factory,
    )

    /**
     * @see [com.intellij.codeInsight.lookup.impl.AsyncRendering.scheduleRendering]
     */
    override suspend fun renderElementSuspending(
        element: LookupElement,
        presentation: LookupElementPresentation,
    ) {
        element.renderElement(presentation)

        element.shortenCommand = readAction {
            // avoiding PsiInvalidElementAccessException in completionFile
            val file = position.element
                ?.containingFile
                ?.copy() as? KtFile
                ?: return@readAction null

            collectPossibleReferenceShortenings(file)
        }?.also {
            logger<FirClassifierCompletionContributor>().debug {
                "Shortened command: $it"
            }
        }
    }

    @RequiresReadLock
    private fun collectPossibleReferenceShortenings(file: KtFile): ShortenCommand? {
        val element = file.findElementAt(offset)
            ?: return null

        val parent = PsiTreeUtil.getParentOfType(
            /* element = */ element,
            /* aClass = */ factory.parentClass.java,
            /* strict = */ true,
        ) ?: return null

        val useSiteElement = factory(fqName)
            .let { parent.replaced<KtElement>(it) }

        return analyze(useSiteElement) {
            collectPossibleReferenceShortenings(
                file = file,
                selection = useSiteElement.textRange,
            )
        }
    }
}

private inline fun <reified R : KtElement> ((String) -> R).asFactory(
): MethodBasedElementFactory<R> = object : MethodBasedElementFactory<R>() {

    override val parentClass: KClass<R>
        get() = R::class

    override fun invoke(text: String): R =
        this@asFactory(text)
}

private abstract class MethodBasedElementFactory<R : KtElement> {

    abstract val parentClass: KClass<R>

    abstract operator fun invoke(text: String): R
}

private operator fun <R : KtElement> MethodBasedElementFactory<R>.invoke(fqName: FqName): R =
    this(text = fqName.render())
