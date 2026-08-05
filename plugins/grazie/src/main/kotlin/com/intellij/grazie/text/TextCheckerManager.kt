package com.intellij.grazie.text

import ai.grazie.gec.model.problem.Problem
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.mlec.MlecChecker
import com.intellij.grazie.spellcheck.SpellingTextChecker
import com.intellij.grazie.text.TextChecker.ProofreadingContext
import com.intellij.grazie.utils.getProblemsForText
import com.intellij.grazie.utils.isSpelling
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.yield
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

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
        return runSpellingChecker(checkers.first(), listOf(context))
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

    /**
     * Runs the registered checkers for a batch of [contexts] while overlapping local analysis with the cloud computation.
     *
     * The scheduling is intentionally split into three phases because the checkers have different read-action and cancellation
     * requirements:
     *
     * 1. The cloud computation used by [MlecChecker] and [SpellingTextChecker] is started immediately in [GrazieScope] on
     *    [blockingDispatcher] (basically repeating the [com.intellij.openapi.progress.util.runWithCheckCanceled]).
     *    It must not be a child of the [runBlockingCancellable] call below: otherwise it inherits the caller's
     *    read-access context, and cancellation has to wait for a suspended network coroutine which still owns that context.
     * 2. [AsyncTreeRuleChecker], [LanguageToolChecker], and compatibility checkers run through the original coroutine scheduler.
     *    External checkers start eagerly, in registration order, so they can suspend while other checkers use the thread. Regular
     *    checkers start lazily and therefore keep the CPU-bound work sequential. This phase inherits the caller's read action, as
     *    required by these checkers. Cancelling this phase also cancels the concurrently running cloud computation.
     * 3. After the local phase, [awaitWithCheckCanceled] waits for the cloud computations. A cancellation aborts this invocation, so no
     *    partial collection is returned, cancels the cloud computation, and is rethrown. Once the cloud result is available,
     *    [MlecChecker] and [SpellingTextChecker] run in order of their registration under RA.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun doRun(checkers: List<TextChecker>, contexts: List<ProofreadingContext>): Collection<TextProblem> {
      if (checkers.isSingleSpellingChecker()) {
        return runSpellingChecker(checkers.first(), contexts)
      }

      val mlecChecker = checkers.filterIsInstance<MlecChecker>().singleOrNull()
      val spellingChecker = checkers.filterIsInstance<SpellingTextChecker>().singleOrNull()
      val project = contexts.first().text.containingFile.project

      // Start cloud work before local analysis. This future is intentionally independent of the RA-owning coroutine hierarchy.
      val cloudComputations = if (mlecChecker != null || spellingChecker != null) {
        GrazieScope.coroutineScope().async(blockingDispatcher) { catching { getProblemsForText(contexts, project) } }.asCompletableFuture()
      } else null

      try {
        return doRun(checkers, contexts, mlecChecker, spellingChecker, cloudComputations)
      }
      catch (e: CancellationException) {
        cloudComputations?.cancel(false)
        throw e
      }
    }

    private fun doRun(
      checkers: List<TextChecker>, contexts: List<ProofreadingContext>,
      mlecChecker: MlecChecker?, spellingChecker: SpellingTextChecker?,
      cloudComputations: CompletableFuture<Map<ProofreadingContext, List<Problem>>?>?,
    ): List<TextProblem> {
      val results = arrayOfNulls<Collection<TextProblem>>(checkers.size)

      // Run read-action-dependent checkers while the cloud computation is performed
      runBlockingCancellable {
        val deferred = checkers.mapIndexedNotNull { index, checker ->
          when (checker) {
            mlecChecker, spellingChecker -> null
            is ExternalTextChecker -> index to async { catching { checker.checkExternally(contexts) } ?: emptyList() }
            else -> index to async(start = CoroutineStart.LAZY) { catching { checker.check(contexts) } ?: emptyList() }
          }
        }

        // let all pending external checker jobs complete what they're ready to do and possibly suspend further
        for ((_, job) in deferred) {
          yield()
          job.start()
        }

        for ((index, job) in deferred) {
          results[index] = job.await()
        }
      }

      // Wait for the network computation to be completed
      val cloudProblems = cloudComputations?.awaitWithCheckCanceled() ?: emptyMap()

      // Cloud data is now complete, so MLEC and SPELL can run under existing RA without suspending on network work.
      for ((index, checker) in checkers.withIndex()) {
        results[index] = when (checker) {
          spellingChecker -> catching { spellingChecker.checkWithProblems(contexts, cloudProblems) } ?: emptyList()
          mlecChecker -> catching { mlecChecker.checkWithProblems(cloudProblems) } ?: emptyList()
          else -> results[index]
        }
      }

      return results.filterNotNull().flatten()
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

    private fun runSpellingChecker(checker: TextChecker, contexts: List<ProofreadingContext>): Collection<TextProblem> {
      // Spelling text checker optimization
      // To get rid of expensive cancellable overhead,
      // in case if cloud checking is disabled
      return catching {
        if (seemsCloudConnected()) {
          runBlockingCancellable {
            (checker as ExternalTextChecker).checkExternally(contexts)
          }
        }
        else {
          checker.check(contexts)
        }
      } ?: emptyList()
    }
  }
}
