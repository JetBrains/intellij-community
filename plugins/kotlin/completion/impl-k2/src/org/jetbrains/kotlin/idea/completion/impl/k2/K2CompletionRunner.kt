// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage
import org.jetbrains.kotlin.idea.completion.impl.k2.ParallelCompletionRunner.Companion.MAX_CONCURRENT_COMPLETION_THREADS
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.KtCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ChainCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.replaceTypeParametersWithStarProjections
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionCollectResultsEvent
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionCommonSectionDataSetupEvent
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionSectionEvent
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.timeEvent
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.ClassifierLookupObject
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getAnnotationLiteralExpectedType
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getEqualityExpectedType
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import java.util.ArrayDeque
import java.util.LinkedList
import java.util.PriorityQueue


internal class K2CompletionRunnerResult(
    val addedElements: Int,
    val registeredChainContributors: List<K2ChainCompletionContributor>,
)

/**
 * Interface responsible for executing completion sections within a completion context.
 * It allows decoupling the definition of [K2CompletionSection]s from the actual execution of the sections,
 * which can be chosen to be either done sequentially or in parallel.
 */
internal interface K2CompletionRunner {
    /**
     * Runs the [sections] within the [completionContext].
     * Implementing classes decide in which way the [sections] are run, be it in parallel or sequential but have
     * to ensure that the completion is cancellable.
     * The call itself is blocking and only completes after completion has finished in its entirety (or has been canceled).
     *
     * Returns the number of elements added to the [CompletionResultSet] in the [completionContext].
     */
    fun <P : KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): K2CompletionRunnerResult

    companion object {
        /**
         * Chooses the preferred [K2CompletionRunner] implementation based on registry settings and the [sectionCount].
         */
        fun getInstance(sectionCount: Int): K2CompletionRunner {
            if (Registry.`is`("kotlin.k2.parallel.completion.enabled", false) && sectionCount > 1) {
                return ParallelCompletionRunner()
            } else {
                return SequentialCompletionRunner()
            }
        }

        /**
         * Runs chain completion, returning true if any new results were added by chain completion.
         */
        @OptIn(KaImplementationDetail::class)
        fun runChainCompletion(
            originalPositionContext: KotlinNameReferencePositionContext,
            completionResultSet: CompletionResultSet,
            parameters: KotlinFirCompletionParameters,
            chainCompletionContributors: List<K2ChainCompletionContributor>,
        ): Boolean {
            val explicitReceiver = originalPositionContext.explicitReceiver ?: return false

            var addedAnyElements = false
            completionResultSet.runRemainingContributors(parameters.delegate) { completionResult ->
                val lookupElement = completionResult.lookupElement
                if (lookupElement.`object` !is KotlinLookupObject) {
                    // Pass on results from other contributors further down that are not chain completion related
                    completionResultSet.passResult(completionResult)
                    return@runRemainingContributors
                }

                val classifierLookupObject = lookupElement.`object` as? ClassifierLookupObject
                val nameToImport = when (val importStrategy = classifierLookupObject?.importingStrategy) {
                    is ImportStrategy.AddImport -> importStrategy.nameToImport
                    is ImportStrategy.InsertFqNameAndShorten -> importStrategy.fqName
                    else -> null
                }

                if (nameToImport == null) {
                    // We need to filter out (i.e. not pass them on) results from the [KotlinChainCompletionContributor]
                    // that are only supposed to be added if they can add an import.
                    // Otherwise, these results would cause unexpected results like KTIJ-35113
                    return@runRemainingContributors
                }

                val expression = KtPsiFactory.contextual(explicitReceiver)
                    .createExpression(nameToImport.render() + "." + originalPositionContext.nameExpression.text) as KtDotQualifiedExpression

                val receiverExpression = expression.receiverExpression as? KtDotQualifiedExpression
                val nameExpression = expression.selectorExpression as? KtNameReferenceExpression

                if (receiverExpression == null
                    || nameExpression == null
                ) {
                    return@runRemainingContributors
                }

                // Chain completion results are run sequentially for now.
                // TODO: Potentially unify with remaining section logic
                analyze(nameExpression) {
                    val newCompletionContext = K2CompletionContext(
                        parameters = parameters,
                        resultSet = completionResultSet,
                        positionContext = KotlinExpressionNameReferencePositionContext(nameExpression, explicitReceiver = receiverExpression)
                    )

                    // TODO: Remove once KT-79109 KaBaseIllegalPsiException is thrown incorrectly when using CodeFragments in KtCompletionExtensionCandidateChecker.create
                    @OptIn(KaImplementationDetail::class)
                    val commonData = createCommonSectionData(newCompletionContext) ?: return@analyze

                    val sink = K2DelegatingLookupElementSink(completionResultSet)

                    chainCompletionContributors.forEach { contributor ->
                        // This cast is safe because the contributor is not used except for extracting the name when debugging
                        @Suppress("UNCHECKED_CAST")
                        val completionContributor =
                            contributor as? K2CompletionContributor<KotlinExpressionNameReferencePositionContext>
                                ?: return@forEach
                        val sectionContext = K2CompletionSectionContext(
                            commonData = commonData,
                            sink = sink,
                            contributor = completionContributor,
                            addLaterSection = { section ->
                                error("Chain completion sections cannot add later sections yet")
                            }
                        )
                        context(sectionContext) {
                            contributor.createChainedLookupElements(receiverExpression, nameToImport)
                                .forEach { lookupElement ->
                                    completionResultSet.addElement(lookupElement)
                                    addedAnyElements = true
                                }
                        }
                    }
                }
            }

            return addedAnyElements
        }
    }
}

context(_: KaSession)
private fun createWeighingContext(
    positionContext: KotlinRawPositionContext,
    parameters: KotlinFirCompletionParameters
): WeighingContext? {
    return when (positionContext) {
        is KotlinNameReferencePositionContext -> {
            val nameExpression = positionContext.nameExpression
            val nameExpressionParent = nameExpression.parent
            val expectedType = when {
                // during the sorting of completion suggestions expected type from position and actual types of suggestions are compared;
                // see `org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher`;
                // currently in case of callable references actual types are calculated incorrectly, which is why we don't use information
                // about expected type at all
                // TODO: calculate actual types for callable references correctly and use information about expected type
                positionContext is KotlinCallableReferencePositionContext -> null
                nameExpression.expectedType != null -> nameExpression.expectedType
                nameExpressionParent is KtBinaryExpression -> getEqualityExpectedType(nameExpression)
                nameExpressionParent is KtCollectionLiteralExpression -> getAnnotationLiteralExpectedType(nameExpression)
                nameExpressionParent is KtThrowExpression -> buildClassType(StandardClassIds.Throwable)
                else -> null
            }
            if (parameters.completionType == CompletionType.SMART
                && expectedType == null
            ) return null // todo move out

            WeighingContext.create(parameters, positionContext, expectedType)
        }

        else -> WeighingContext.create(parameters, elementInCompletionFile = positionContext.position)
    }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
internal fun createExtensionChecker(
    positionContext: KotlinRawPositionContext,
    originalFile: KtFile,
    runtimeType: KaType?,
): KaCompletionExtensionCandidateChecker? {
    val positionContext = positionContext as? KotlinSimpleNameReferencePositionContext ?: return null
    val receiver = positionContext.explicitReceiver
    val runtimeTypeWithErasedTypeParameters = runtimeType?.replaceTypeParametersWithStarProjections()?.render(position = Variance.INVARIANT)
    val runtimeTypeClassId = runtimeType?.symbol?.classId
    val castedReceiver = if (runtimeTypeWithErasedTypeParameters == null || runtimeTypeClassId == receiver?.expressionType?.symbol?.classId) {
        receiver
    } else if (receiver != null) {
        // FIXME: check extensions applicable to the runtime type properly KTIJ-35532
        val codeFragment = "(${receiver.text} as $runtimeTypeWithErasedTypeParameters)"
        KtPsiFactory.contextual(receiver).createExpression(codeFragment)
    } else {
        null
    }
    // FIXME: KTIJ-34285
    @OptIn(KaImplementationDetail::class)
    return KaBaseIllegalPsiException.allowIllegalPsiAccess {
        KtCompletionExtensionCandidateChecker.create(
            originalFile = originalFile,
            nameExpression = positionContext.nameExpression,
            explicitReceiver = castedReceiver
        )
    }
}

private fun <P : KotlinRawPositionContext> KaSession.createCommonSectionData(
    completionContext: K2CompletionContext<P>,
): K2CompletionSectionCommonData<P>? {
    CompletionCommonSectionDataSetupEvent().timeEvent {
        val parameters = completionContext.parameters
        val positionContext = completionContext.positionContext
        val weighingContext = createWeighingContext(positionContext, parameters) ?: return null
        val visibilityChecker = CompletionVisibilityChecker(parameters)
        val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
        val importStrategyDetector = ImportStrategyDetector(parameters.originalFile, parameters.originalFile.project)

        return K2CompletionSectionCommonData(
            completionContext = completionContext,
            weighingContext = weighingContext,
            prefixMatcher = completionContext.resultSet.prefixMatcher,
            visibilityChecker = visibilityChecker,
            symbolFromIndexProvider = symbolFromIndexProvider,
            importStrategyDetector = importStrategyDetector,
            session = this@createCommonSectionData,
        )
    }
}

/**
 * A basic priority queue that maintains two internal queues for managing a queue of [initialElements].
 * - The first queue contains all the [initialElements] and can potentially be shared between multiple elements.
 * - The second queue contains only elements that have been added to the local instance obtained via [createLocalInstance].
 *
 * The purpose of this class is to model a priority queue of elements between different threads, where each thread
 * may add elements to its own local queue that should not affect other threads.
 */
private class SharedPriorityQueue<P : Any, C : Comparable<C>>(
    initialElements: Collection<P>,
    comparatorSelector: (P) -> Comparable<C>
) {
    private val comparator = compareBy(comparatorSelector)
    private val globalQueue: ArrayDeque<P> = ArrayDeque(initialElements.sortedWith(comparator))

    inner class LocalInstance {
        private var insertionCounter: Int = 0

        private inner class LocalQueueEntry(val element: P, val insertionOrder: Int) : Comparable<LocalQueueEntry> {
            override fun compareTo(other: LocalQueueEntry): Int {
                val compareValue = comparator.compare(this.element, other.element)
                if (compareValue != 0) return compareValue
                return this.insertionOrder - other.insertionOrder
            }
        }

        private val localQueue: PriorityQueue<LocalQueueEntry> = PriorityQueue()

        /**
         * Pops the first element from the global queue or from the local queue.
         *
         * The order of elements from this queue is determined by the first element of the corresponding queues.
         * - If an element from one queue is strictly smaller than the other, then that element is returned first.
         * - Elements from the local queue are preferred if there is a tie from the comparator.
         */
        fun popFirst(): P? = synchronized(globalQueue) {
            val localFirst = localQueue.peek()?.element
            val globalFirst = globalQueue.peek()
            if (localFirst == null && globalFirst == null) return null
            if (globalFirst != null && (localFirst == null || comparator.compare(globalFirst, localFirst) < 0)) {
                globalQueue.pop()
                return globalFirst
            } else {
                localQueue.remove()
                return localFirst
            }
        }

        /**
         * Adds an element to the local queue.
         * The element will be added according to the [comparator] but added after existing elements in case of a tie from the comparator.
         */
        fun addLocal(element: P) {
            localQueue.add(LocalQueueEntry(element, insertionCounter++))
        }
    }

    fun createLocalInstance(): LocalInstance = LocalInstance()
}

context(_: KaSession, context: K2CompletionSectionContext<P>)
private fun <P : KotlinRawPositionContext> K2CompletionSection<P>.executeIfAllowed() {
    if (!contributor.shouldExecute()) return

    CompletionSectionEvent(
        contributorName = contributor::class.simpleName ?: "Unknown",
        sectionName = name.takeIf { it != contributor::class.simpleName }
    ).timeEvent {
        runnable()
    }
}

/**
 * This completion runner executes all sections in sequence all in the same analysis session.
 */
private class SequentialCompletionRunner : K2CompletionRunner {
    override fun <P : KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): K2CompletionRunnerResult {
        val parameters = completionContext.parameters
        val resultSet = completionContext.resultSet
        val sink = K2DelegatingLookupElementSink(resultSet)

        val remainingSections = LinkedList(sections.toMutableList())
        remainingSections.sortBy { it.priority }

        val globalAndLocalQueue = SharedPriorityQueue(remainingSections) { it.priority }.createLocalInstance()

        analyze(parameters.completionFile) {
            val commonData = createCommonSectionData(completionContext) ?: return@analyze

            while (true) {
                val section = globalAndLocalQueue.popFirst()
                if (section == null) break
                ProgressManager.checkCanceled()

                // We can share the same sink for all sections when running completion sequentially.
                val sectionContext = K2CompletionSectionContext(
                    commonData = commonData,
                    contributor = section.contributor,
                    sink = sink,
                    addLaterSection = { section ->
                        globalAndLocalQueue.addLocal(section)
                    },
                )

                // We make sure we have the correct position before running the completion section.
                context(sectionContext) {
                    section.executeIfAllowed()
                }
            }
        }

        return K2CompletionRunnerResult(
            addedElements = sink.addedElementCount,
            registeredChainContributors = sink.registeredChainContributors,
        )
    }
}


private typealias K2ParallelCompletionEntry<P> = Pair<K2CompletionSection<P>, K2AccumulatingLookupElementSink>

/**
 * This completion runner executes sections in parallel within up to [MAX_CONCURRENT_COMPLETION_THREADS]
 * independent analysis sessions/threads.
 * It is ensured that the sections are _started_ in the order that they are provided to the [runCompletion] method.
 * The runner also ensures that the elements are added to the [CompletionResultSet] in the same order as they were added
 * by the sections, which is identical to sequential execution.
 */
private class ParallelCompletionRunner : K2CompletionRunner {
    private companion object {
        // There is currently no point of using more than 4 threads due to the lack of "heavy" completion contributors.
        // Currently, only 2 contributors are considered "heavy".
        private const val MAX_CONCURRENT_COMPLETION_THREADS = 4
    }

    private val maxCompletionThreads
        get() = Runtime.getRuntime().availableProcessors().coerceIn(1..MAX_CONCURRENT_COMPLETION_THREADS)

    private fun <P : KotlinRawPositionContext> handleElement(
        resultSet: CompletionResultSet,
        registeredChainContributors: MutableList<K2ChainCompletionContributor>,
        registeredSinks: SharedPriorityQueue<Pair<K2CompletionSection<P>, K2AccumulatingLookupElementSink>, K2ContributorSectionPriority>.LocalInstance,
        element: AccumulatingSinkMessage
    ) = when (element) {
        is AccumulatingSinkMessage.ElementBatch -> {
            resultSet.addAllElements(element.elements)
        }

        is AccumulatingSinkMessage.RestartCompletionOnPrefixChange -> {
            resultSet.restartCompletionOnPrefixChange(element.prefixCondition)
        }

        is AccumulatingSinkMessage.RestartCompletionOnAnyPrefixChange -> {
            resultSet.restartCompletionOnAnyPrefixChange()
        }

        is AccumulatingSinkMessage.SingleElement -> {
            resultSet.addElement(element.element)
        }

        is AccumulatingSinkMessage.RegisterChainContributor -> registeredChainContributors.add(element.chainContributor)

        is AccumulatingSinkMessage.RegisterLaterSectionSink ->
            @Suppress("UNCHECKED_CAST")
            registeredSinks.addLocal(element.section as K2CompletionSection<P> to element.sink)
    }

    private class WeighingContextCreationImpossibleException : RuntimeException()

    private fun <P : KotlinRawPositionContext> performCompletion(
        completionContext: K2CompletionContext<P>,
        remainingSectionsQueue: SharedPriorityQueue<K2ParallelCompletionEntry<P>, K2ContributorSectionPriority>,
    ) = analyze(completionContext.parameters.completionFile) {
        // We need to create one common data (containing weighing context and similar constructs) per session
        val commonData = createCommonSectionData(completionContext)
            ?: throw WeighingContextCreationImpossibleException()
        val localQueueInstance = remainingSectionsQueue.createLocalInstance()

        while (true) {
            val entry = localQueueInstance.popFirst()
            if (entry == null) break
            @Suppress("ForbiddenInSuspectContextMethod")
            ProgressManager.checkCanceled()

            val (currentSection, sectionSink) = entry

            // We need an individual context for each section because the sink of each section is different.
            val sectionContext = K2CompletionSectionContext(
                commonData = commonData,
                sink = sectionSink,
                contributor = currentSection.contributor,
                addLaterSection = { section ->
                    val laterSectionSink = K2AccumulatingLookupElementSink()
                    localQueueInstance.addLocal(section to laterSectionSink)
                    sectionSink.registerLaterSection(section, section.priority, laterSectionSink)
                },
            )

            try {
                context(sectionContext) {
                    currentSection.executeIfAllowed()
                }
            } finally {
                sectionSink.close()
            }
        }
    }

    private suspend fun <P : KotlinRawPositionContext> collectCompletions(
        sectionsWithSinks: List<K2ParallelCompletionEntry<P>>,
        resultSet: CompletionResultSet,
    ): K2CompletionRunnerResult {
        var addedElements = 0
        val registeredSinks = SharedPriorityQueue(sectionsWithSinks) { it.first.priority }.createLocalInstance()

        val registeredChainContributors = mutableListOf<K2ChainCompletionContributor>()
        while (true) {
            val entry = registeredSinks.popFirst()
            if (entry == null) break

            val (currentSection, sectionSink) = entry

            val event = CompletionCollectResultsEvent(
                contributorName = currentSection.contributor::class.simpleName ?: "Unknown",
                sectionName = currentSection.name.takeIf { it != currentSection.contributor::class.simpleName }
            )

            event.timeEvent {
                sectionSink.consumeElements { element ->
                    handleElement(
                        resultSet = resultSet,
                        registeredChainContributors = registeredChainContributors,
                        registeredSinks = registeredSinks,
                        element = element
                    )
                }

                event.consumedElements = sectionSink.addedElementCount
                addedElements += sectionSink.addedElementCount
            }
        }

        return K2CompletionRunnerResult(
            addedElements = addedElements,
            registeredChainContributors = registeredChainContributors,
        )
    }

    override fun <P : KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): K2CompletionRunnerResult {
        // Each section gets its own sink which is then later collected by the main thread in the same order as the sections
        val sectionsWithSinks = sections.map { it to K2AccumulatingLookupElementSink() }
        // This is the queue of all remaining sections ordered by their priority.
        // Each thread will pick the remaining section with the highest priority when choosing a new section to run.
        val remainingSectionsQueue = SharedPriorityQueue(sectionsWithSinks) { it.first.priority }

        // If this block is canceled, so are all the threads launched inside the scope.
        return try {
            runBlockingCancellable {
                repeat(maxCompletionThreads.coerceAtMost(sectionsWithSinks.size)) { _ ->
                    launch(Dispatchers.Default) {
                        performCompletion(completionContext, remainingSectionsQueue)
                    }
                }
                collectCompletions(sectionsWithSinks, completionContext.resultSet)
            }
        } catch (_: WeighingContextCreationImpossibleException) {
            K2CompletionRunnerResult(0, emptyList())
        } finally {
            sectionsWithSinks.forEach { it.second.cancel() }
        }
    }
}
