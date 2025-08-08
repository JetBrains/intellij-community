// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage
import org.jetbrains.kotlin.idea.completion.impl.k2.ParallelCompletionRunner.Companion.MAX_CONCURRENT_COMPLETION_THREADS
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.KtCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getAnnotationLiteralExpectedType
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getEqualityExpectedType
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import java.util.*


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
    ): Int

    companion object {
        /**
         * Chooses the preferred [K2CompletionRunner] implementation based on registry settings and the [sectionCount].
         */
        fun getInstance(sectionCount: Int): K2CompletionRunner {
            if (Registry.`is`("kotlin.k2.parallel.completion.enabled") && sectionCount > 1) {
                return ParallelCompletionRunner()
            } else {
                return SequentialCompletionRunner()
            }
        }
    }
}

context(KaSession)
private fun createWeighingContext(
    positionContext: KotlinRawPositionContext,
    parameters: KotlinFirCompletionParameters
): WeighingContext? {
    return when (positionContext) {
        is KotlinNameReferencePositionContext -> {
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
            if (parameters.completionType == CompletionType.SMART
                && expectedType == null
            ) return null // todo move out

            WeighingContext.create(parameters, positionContext, expectedType)
        }

        else -> WeighingContext.create(parameters, elementInCompletionFile = positionContext.position)
    }
}

context(KaSession)
private fun createExtensionChecker(
    positionContext: KotlinRawPositionContext,
    originalFile: KtFile,
): KaCompletionExtensionCandidateChecker? {
    val positionContext = positionContext as? KotlinSimpleNameReferencePositionContext ?: return null
    // FIXME: KTIJ-34285
    @OptIn(KaImplementationDetail::class)
    return KaBaseIllegalPsiException.allowIllegalPsiAccess {
        KtCompletionExtensionCandidateChecker.create(
            originalFile = originalFile,
            nameExpression = positionContext.nameExpression,
            explicitReceiver = positionContext.explicitReceiver
        )
    }
}

private fun <P : KotlinRawPositionContext> KaSession.createCommonSectionData(
    completionContext: K2CompletionContext<P>,
): K2CompletionSectionCommonData<P>? {
    val parameters = completionContext.parameters
    val positionContext = completionContext.positionContext
    val weighingContext = createWeighingContext(positionContext, parameters) ?: return null
    val visibilityChecker = CompletionVisibilityChecker(parameters)
    val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
    val importStrategyDetector = ImportStrategyDetector(parameters.originalFile, parameters.originalFile.project)
    val extensionChecker by lazy { createExtensionChecker(positionContext, parameters.originalFile) }

    return K2CompletionSectionCommonData(
        completionContext = completionContext,
        weighingContext = weighingContext,
        prefixMatcher = completionContext.resultSet.prefixMatcher,
        visibilityChecker = visibilityChecker,
        symbolFromIndexProvider = symbolFromIndexProvider,
        importStrategyDetector = importStrategyDetector,
        extensionCheckerProvider = { extensionChecker },
    )
}

/**
 * A basic priority queue that maintains two internal queues for managing a queue of [initialElements].
 * - The first queue contains all the [initialElements] and can potentially be shared between multiple elements.
 * - The second queue contains only elements that have been added to the local instance obtained via [getLocalInstance].
 *
 * The purpose of this class is to model a priority queue of elements between different threads, where each thread
 * may add elements to its own local queue that should not affect other threads.
 */
private class SharedPriorityQueue<P, C: Comparable<C>>(
    initialElements: Collection<P>,
    comparatorSelector: (P) -> Comparable<C>
) {
    private val comparator = compareBy(comparatorSelector)
    private val globalQueue: LinkedList<P> = LinkedList(initialElements)

    init {
      globalQueue.sortWith(comparator)
    }

    inner class LocalInstance {
        private val localQueue: LinkedList<P> = LinkedList()

        /**
         * Pops the first element from the global queue or from the local queue.
         *
         * The order of elements from this queue is determined by the first element of the corresponding queues.
         * - If an element from one queue is strictly smaller than the other, then that element is returned first.
         * - Elements from the local queue are preferred if there is a tie from the comparator.
         */
        fun popFirst(): P? = synchronized(globalQueue) {
            val localFirst = localQueue.peek()
            val globalFirst = globalQueue.peek()
            if (localFirst == null && globalFirst == null) return null
            if (globalFirst != null && (localFirst == null || comparator.compare(globalFirst, localFirst) < 0)) {
                globalQueue.pop()
                return globalFirst
            } else {
                localQueue.pop()
                return localFirst
            }
        }

        /**
         * Adds an element to the local queue.
         * The element will be added according to the [comparator] but added after existing elements in case of a tie from the comparator.
         */
        fun addLocal(element: P) {
            localQueue.addLast(element)
            // Performance here could be optimized but is unlikely to be a problem in practice because the lists we are dealing
            // with are very small (<20 elements)
            localQueue.sortWith(comparator)
        }
    }

    fun getLocalInstance() : LocalInstance = LocalInstance()
}

/**
 * This completion runner executes all sections in sequence all in the same analysis session.
 */
private class SequentialCompletionRunner : K2CompletionRunner {
    override fun <P : KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): Int {
        val parameters = completionContext.parameters
        val resultSet = completionContext.resultSet
        val sink = K2DelegatingLookupElementSink(resultSet)

        val remainingSections = LinkedList(sections.toMutableList())
        remainingSections.sortBy { it.priority }

        val globalAndLocalQueue = SharedPriorityQueue(remainingSections) { it.priority }.getLocalInstance()

        analyze(parameters.completionFile) {
            val commonData = createCommonSectionData(completionContext) ?: return@analyze

            while (true) {
                val section = globalAndLocalQueue.popFirst()
                if (section == null) break
                ProgressManager.checkCanceled()

                // We can share the same sink for all sections when running completion sequentially.
                val sectionContext = K2CompletionSectionContext(
                    commonData = commonData,
                    section = section,
                    sink = sink,
                    addLaterSection = { section ->
                        globalAndLocalQueue.addLocal(section)
                    },
                )
                // We make sure we have the correct position before running the completion section.
                section.runnable(this@analyze, sectionContext)
            }
        }

        return sink.addedElementCount
    }
}


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

    private fun handleElement(
        resultSet: CompletionResultSet,
        element: AccumulatingSinkMessage
    ) = when (element) {
        is AccumulatingSinkMessage.ElementBatch -> {
            resultSet.addAllElements(element.elements)
        }

        is AccumulatingSinkMessage.RestartCompletionOnPrefixChange -> {
            resultSet.restartCompletionOnPrefixChange(element.prefixCondition)
        }

        is AccumulatingSinkMessage.SingleElement -> {
            resultSet.addElement(element.element)
        }

        is AccumulatingSinkMessage.RegisterChainContributor -> TODO()

        else -> {}
    }

    override fun <P : KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): Int {
        val parameters = completionContext.parameters
        val resultSet = completionContext.resultSet

        // Each section gets its own sink which is then later collected by the main thread in the same order as the sections
        val sectionsWithSinks = sections.map { it to K2AccumulatingLookupElementSink() }
        // This is the queue of all remaining sections ordered by their priority. Each thread will pick the
        // remaining section with the highest priority when choosing a new section to run.
        val remainingSectionsQueue = SharedPriorityQueue(sectionsWithSinks) { it.first.priority}

        // If this block is canceled, so are all the threads launched inside the scope.
        return runBlockingCancellable {
            try {
                repeat(maxCompletionThreads.coerceAtMost(sectionsWithSinks.size)) { _ ->
                    launch(Dispatchers.Default) {
                        // Each thread gets its own analysis session, which also requires creating
                        // associated utility objects like the visibility checker per thread.
                        analyze(parameters.completionFile) {
                            // We need to create one common data (containing weighing context and similar constructs) per session
                            val commonData = createCommonSectionData(completionContext) ?: return@analyze
                            val localQueueInstance = remainingSectionsQueue.getLocalInstance()

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
                                    section = currentSection,
                                    addLaterSection = { section ->
                                        val laterSectionSink = K2AccumulatingLookupElementSink()
                                        localQueueInstance.addLocal(section to laterSectionSink)
                                        sectionSink.registerLaterSection(section.priority, laterSectionSink)
                                    },
                                )

                                try {
                                    currentSection.runnable(this@analyze, sectionContext)
                                } finally {
                                    sectionSink.close()
                                }
                            }
                        }
                    }
                }

                var addedElements = 0
                val initiallyRegisteredSinks = SharedPriorityQueue(sectionsWithSinks) { it.first.priority }.getLocalInstance()

                while (true) {
                    val entry = initiallyRegisteredSinks.popFirst()
                    if (entry == null) break

                    val (currentSection, sectionSink) = entry

                    sectionSink.consumeElements { element ->
                        if (element is AccumulatingSinkMessage.RegisterLaterSectionSink) {
                            initiallyRegisteredSinks.addLocal(currentSection to element.sink)
                        } else {
                            handleElement(resultSet, element)
                        }
                    }
                    addedElements += sectionSink.addedElementCount
                }

                addedElements
            } finally {
                sectionsWithSinks.forEach { it.second.cancel() }
            }
        }
    }
}
