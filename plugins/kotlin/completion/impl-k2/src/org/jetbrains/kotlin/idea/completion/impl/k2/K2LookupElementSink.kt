// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.ElementPattern
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage.ElementBatch
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage.RegisterChainContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage.RegisterLaterSectionSink
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage.RestartCompletionOnPrefixChange
import org.jetbrains.kotlin.idea.completion.impl.k2.K2AccumulatingLookupElementSink.AccumulatingSinkMessage.SingleElement
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ChainCompletionContributor
import java.util.concurrent.atomic.AtomicInteger

/**
 * A sink for adding elements and
 */
internal interface K2LookupElementSink {
    /**
     * Adds a single [element] to the sink.
     */
    fun addElement(element: LookupElement)

    /**
     * Adds a batch of [elements] to the sink.
     * The purpose of this method is that adding an entire batch might be less jarring to the user in the UI
     * than adding each element individually.
     */
    fun addElements(elements: Iterable<LookupElement>)

    /**
     * Instructs completion to restart completion if the prefix changes according to the [prefixCondition].
     * See [CompletionResultSet.restartCompletionOnPrefixChange].
     */
    fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>)

    /**
     * Registers a [K2ChainCompletionContributor] for later processing.
     */
    fun registerChainContributor(chainContributor: K2ChainCompletionContributor)

    /**
     * Returns the number of elements added through the [addElement] and [addElements] methods.
     */
    val addedElementCount: Int
}

/**
 * A [K2LookupElementSink] that delegates to the [resultSet] for adding elements.
 */
internal class K2DelegatingLookupElementSink(
    private val resultSet: CompletionResultSet,
    internal val registeredChainContributors: MutableList<K2ChainCompletionContributor> = mutableListOf(),
) : K2LookupElementSink {
    private val addedElementCounter: AtomicInteger = AtomicInteger(0)
    override val addedElementCount: Int
        get() = addedElementCounter.get()

    override fun addElement(element: LookupElement) {
        addedElementCounter.incrementAndGet()
        resultSet.addElement(element)
    }

    override fun addElements(elements: Iterable<LookupElement>) {
        resultSet.addAllElements(elements)
        addedElementCounter.addAndGet(elements.count())
    }

    override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {
        resultSet.restartCompletionOnPrefixChange(prefixCondition)
    }

    override fun registerChainContributor(chainContributor: K2ChainCompletionContributor) {
        registeredChainContributors.add(chainContributor)
    }
}

/**
 * A [K2LookupElementSink] that accumulates elements in a message queue for later consumption.
 * This is useful for parallel execution where we accumulate elements that are then processed in the main thread later on.
 */
internal class K2AccumulatingLookupElementSink() : K2LookupElementSink {
    internal sealed interface AccumulatingSinkMessage {
        class SingleElement(val element: LookupElement) : AccumulatingSinkMessage
        class ElementBatch(val elements: List<LookupElement>) : AccumulatingSinkMessage
        class RestartCompletionOnPrefixChange(val prefixCondition: ElementPattern<String>) : AccumulatingSinkMessage
        class RegisterChainContributor(val chainContributor: K2ChainCompletionContributor) : AccumulatingSinkMessage
        class RegisterLaterSectionSink(val priority: K2ContributorSectionPriority, val sink: K2AccumulatingLookupElementSink) : AccumulatingSinkMessage
    }

    // We use batches of LookupElements so they may be added in batches for nicer UX
    private val elementChannel = Channel<AccumulatingSinkMessage>(Channel.UNLIMITED)

    override fun addElement(element: LookupElement) {
        addedElementCounter.incrementAndGet()
        elementChannel.trySend(SingleElement(element))
    }

    override fun addElements(elements: Iterable<LookupElement>) {
        val elementList = elements.toList()
        addedElementCounter.addAndGet(elementList.size)
        elementChannel.trySend(ElementBatch(elementList))
    }

    private val addedElementCounter: AtomicInteger = AtomicInteger(0)
    override val addedElementCount: Int
        get() = addedElementCounter.get()

    /**
     * Used by the producer to indicate that no more elements will be added to this sink.
     * See [Channel.close].
     */
    fun close() {
        elementChannel.close()
    }

    /**
     * Used by the consumer to cancel producing new elements and to indicate to stop the producer.
     * See [Channel.cancel].
     */
    fun cancel() {
        elementChannel.cancel()
    }

    suspend fun consumeElements(f : (AccumulatingSinkMessage) -> Unit) {
        elementChannel.consumeEach(f)
    }

    override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {
        elementChannel.trySend(RestartCompletionOnPrefixChange(prefixCondition))
    }

    override fun registerChainContributor(chainContributor: K2ChainCompletionContributor) {
        elementChannel.trySend(RegisterChainContributor(chainContributor))
    }

    fun registerLaterSection(priority: K2ContributorSectionPriority, sink: K2AccumulatingLookupElementSink) {
        elementChannel.trySend(RegisterLaterSectionSink(priority, sink))
    }
}