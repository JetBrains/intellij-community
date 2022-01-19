// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.onClosed
import java.lang.System.currentTimeMillis


interface CompletionBenchmarkSink {
    fun onCompletionStarted(completionSession: CompletionSession)
    fun onCompletionEnded(completionSession: CompletionSession, canceled: Boolean)
    fun onFlush(completionSession: CompletionSession)

    companion object {

        fun enableAndGet(): Impl = Impl().also { _instance = it }

        fun disable() {
            _instance.let { (it as? Impl)?.channel?.close() }
            _instance = Empty
        }

        val instance get() = _instance
        private var _instance: CompletionBenchmarkSink = Empty
    }

    private object Empty : CompletionBenchmarkSink {
        override fun onCompletionStarted(completionSession: CompletionSession) {}

        override fun onCompletionEnded(completionSession: CompletionSession, canceled: Boolean) {}

        override fun onFlush(completionSession: CompletionSession) {}
    }

    class Impl : CompletionBenchmarkSink {
        private val pendingSessions = mutableListOf<CompletionSession>()
        val channel = Channel<CompletionBenchmarkResults>(capacity = CONFLATED)

        private val perSessionResults = LinkedHashMap<CompletionSession, PerSessionResults>()
        private var start: Long = 0

        override fun onCompletionStarted(completionSession: CompletionSession) = synchronized(this) {
            if (pendingSessions.isEmpty())
                start = currentTimeMillis()
            pendingSessions += completionSession
            perSessionResults[completionSession] = PerSessionResults()
        }

        override fun onCompletionEnded(completionSession: CompletionSession, canceled: Boolean) = synchronized(this) {
            pendingSessions -= completionSession
            perSessionResults[completionSession]?.onEnd(canceled)
            if (pendingSessions.isEmpty()) {
                val firstFlush = perSessionResults.values.filterNot { results -> results.canceled }.minOfOrNull { it.firstFlush } ?: 0
                val full = perSessionResults.values.maxOfOrNull { it.full } ?: 0
                channel.trySend(CompletionBenchmarkResults(firstFlush, full)).onClosed { throw IllegalStateException(it) }
                reset()
            }
        }

        override fun onFlush(completionSession: CompletionSession) = synchronized(this) {
            perSessionResults[completionSession]?.onFirstFlush()
            Unit
        }

        fun reset() = synchronized(this) {
            pendingSessions.clear()
            perSessionResults.clear()
        }

        data class CompletionBenchmarkResults(var firstFlush: Long = 0, var full: Long = 0)

        private inner class PerSessionResults {
            var firstFlush = 0L
            var full = 0L
            var canceled = false

            fun onFirstFlush() {
                firstFlush = currentTimeMillis() - start
            }

            fun onEnd(canceled: Boolean) {
                full = currentTimeMillis() - start
                this.canceled = canceled
            }
        }
    }
}
