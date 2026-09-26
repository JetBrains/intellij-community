package com.intellij.grazie.text

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.utils.isSpelling
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield

internal class TextCheckerManager {

  companion object {
    /**
     * We want for the CPU-bound checkers to all happen on the same thread
     * because other threads are all needed by other inspections during highlighting.
     * But we also want for external checkers to make their network requests in parallel.
     *
     * So we split the checkers into coroutines but dispatch them on the same thread sequentially.
     * We schedule the external checkers to start as soon as possible
     * to allow them to make the requests and suspend, giving up the thread to others.
     * Then we explicitly start the non-external checkers to do their work, probably CPU-bound.
     * We periodically yield to allow the external checkers to process their network responses (if any) and possibly suspend further.
     *
     * In the end, we still collect the results in the checker registration order
     * so that problems from the first checkers can override intersecting problems from others.
     */
    @Deprecated("Use doRun(checkers, contexts) instead")
    fun doRun(checkers: List<TextChecker>, context: ProofreadingContext): Collection<TextProblem> {
      if (checkers.isSingleSpellingChecker()) {
        return catching { checkers.first().check(listOf(context)) } ?: emptyList()
      }

      return runBlockingCancellable {
        val deferred = checkers.map { checker ->
          when (checker) {
            is ExternalTextChecker -> async { catching { checker.checkExternally(context) } ?: emptyList() }
            else -> async(start = CoroutineStart.LAZY) { catching { checker.check(context) } ?: emptyList() }
          }
        }
        for (job in deferred) {
          yield() // let all pending external checker jobs complete what they're ready to do and possibly suspend further
          job.start()
        }
        deferred.awaitAll().flatten()
      }
    }

    fun doRun(checkers: List<TextChecker>, contexts: List<ProofreadingContext>): Collection<TextProblem> {
      if (checkers.isSingleSpellingChecker()) {
        return catching { checkers.first().check(contexts) } ?: emptyList()
      }

      return runBlockingCancellable {
        val deferred = checkers.map { checker ->
          when (checker) {
            is ExternalTextChecker -> async { catching { checker.checkExternally(contexts) } ?: emptyList() }
            else -> async(start = CoroutineStart.LAZY) { catching { checker.check(contexts) } ?: emptyList() }
          }
        }
        for (job in deferred) {
          yield() // let all pending external checker jobs complete what they're ready to do and possibly suspend further
          job.start()
        }
        deferred.awaitAll().flatten()
      }
    }

    private inline fun <T> catching(block: () -> T): T? {
      try {
        return block()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        thisLogger().error(e)
        return null
      }
    }

    private fun List<TextChecker>.isSingleSpellingChecker(): Boolean {
      return size == 1 && first().isSpelling()
    }
  }
}
