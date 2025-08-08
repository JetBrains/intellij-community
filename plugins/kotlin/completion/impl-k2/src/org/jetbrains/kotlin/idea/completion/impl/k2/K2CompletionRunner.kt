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
import java.util.concurrent.LinkedBlockingQueue


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
    fun <P: KotlinRawPositionContext> runCompletion(
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

/**
 * This completion runner executes all sections in sequence all in the same analysis session.
 */
private class SequentialCompletionRunner : K2CompletionRunner {
    override fun <P: KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): Int {
        val parameters = completionContext.parameters
        val positionContext = completionContext.positionContext
        val resultSet = completionContext.resultSet
        val sink = K2DelegatingLookupElementSink(resultSet)
        val project = parameters.originalFile.project
        analyze(parameters.completionFile) {
            val weighingContext = createWeighingContext(positionContext, parameters) ?: return@analyze
            val visibilityChecker = CompletionVisibilityChecker(parameters)
            val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
            val importStrategyDetector = ImportStrategyDetector(parameters.originalFile, project)
            val extensionChecker by lazy { createExtensionChecker(positionContext, parameters.originalFile) }

            // We can share the same section context between all sections because we are operating in the same analysis session,
            // and we use the same sink for all sections.
            val sectionContext = K2CompletionSectionContext(
                completionContext = completionContext,
                sink = sink,
                weighingContext = weighingContext,
                prefixMatcher = resultSet.prefixMatcher,
                visibilityChecker = visibilityChecker,
                symbolFromIndexProvider = symbolFromIndexProvider,
                importStrategyDetector = importStrategyDetector,
                extensionCheckerProvider = { extensionChecker }
            )
            sections.forEach { section ->
                ProgressManager.checkCanceled()
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

    private fun handleElement(resultSet: CompletionResultSet, element: AccumulatingSinkMessage) = when (element) {
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
    }

    override fun <P: KotlinRawPositionContext> runCompletion(
        completionContext: K2CompletionContext<P>,
        sections: List<K2CompletionSection<P>>,
    ): Int {
        val parameters = completionContext.parameters
        val positionContext = completionContext.positionContext
        val resultSet = completionContext.resultSet

        // Each section gets its own sink which is then later collected by the main thread in the same order as the sections
        val sectionSinks = sections.map { it to K2AccumulatingLookupElementSink() }
        // This is the queue of all remaining sections ordered by their priority. Each thread will pick the
        // remaining section with the highest priority when choosing a new section to run.
        val remainingSections = LinkedBlockingQueue<Pair<K2CompletionSection<P>, K2AccumulatingLookupElementSink>>()
        remainingSections.addAll(sectionSinks)
        val project = parameters.originalFile.project

        // If this block is canceled, so are all the threads launched inside the scope.
        return runBlockingCancellable {
            try {
                repeat(maxCompletionThreads.coerceAtMost(remainingSections.size)) {
                    launch(Dispatchers.Default) {
                        // Each thread gets its own analysis session, which also requires creating
                        // associated utility objects like the visibility checker per thread.
                        analyze(parameters.completionFile) {
                            // We create one weighing context and visibility checker per thread
                            val visibilityChecker = CompletionVisibilityChecker(parameters)
                            val weighingContext = createWeighingContext(positionContext, parameters) ?: return@analyze
                            val symbolFromIndexProvider = KtSymbolFromIndexProvider(parameters.completionFile)
                            val importStrategyDetector = ImportStrategyDetector(parameters.originalFile, project)
                            var entry = remainingSections.poll()
                            val extensionChecker by lazy { createExtensionChecker(positionContext, parameters.originalFile) }

                            while (entry != null) {
                                val (currentSection, sectionSink) = entry

                                // We need an individual context for each section because the sink of each section is different.
                                val sectionContext = K2CompletionSectionContext(
                                    completionContext = completionContext,
                                    sink = sectionSink,
                                    weighingContext = weighingContext,
                                    prefixMatcher = resultSet.prefixMatcher,
                                    visibilityChecker = visibilityChecker,
                                    symbolFromIndexProvider = symbolFromIndexProvider,
                                    importStrategyDetector = importStrategyDetector,
                                    extensionCheckerProvider = { extensionChecker }
                                )

                                try {
                                    currentSection.runnable(this@analyze, sectionContext)
                                } finally {
                                    sectionSink.close()
                                }

                                entry = remainingSections.poll()
                            }
                        }
                    }
                }

                var addedElements = 0
                sectionSinks.forEach { (_, channel) ->
                    channel.consumeElements { handleElement(resultSet, it) }
                    addedElements += channel.addedElementCount
                }
                addedElements
            } finally {
                sectionSinks.forEach { it.second.cancel() }
            }
        }
    }
}
