package org.jetbrains.completion.full.line.platform

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.platform.diagnostics.logger
import org.jetbrains.completion.full.line.providers.FullLineCompletionProvider
import org.jetbrains.concurrency.runAsync
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*
 * Delegates FL query to all available providers asynchronously. Allows to get results in batches.
 */
class AsyncFullLineRequest private constructor(
  providersCount: Int,
  private val queue: LinkedBlockingQueue<RequestResult>
) {

  private var remainingProviders = providersCount

  fun isFinished(): Boolean = remainingProviders == 0

  fun getAvailableProposals(): List<RawFullLineProposal> {
    return queue.poll().asResult()
  }

  fun waitForProposals(timeout: Long): List<RawFullLineProposal> {
    val start = System.currentTimeMillis()
    val indicator = ProgressManager.getInstance().progressIndicator
    while (System.currentTimeMillis() - start < timeout) {
      indicator.checkCanceled()
      val result = queue.poll(10, TimeUnit.MILLISECONDS)
      if (result != null) {
        return result.asResult()
      }
    }
    return emptyList()
  }

  private fun RequestResult?.asResult(): List<RawFullLineProposal> {
    if (this == null) {
      return emptyList()
    }
    if (isLast) {
      remainingProviders -= 1
    }

    return proposals
  }

  companion object {
    private val LOG = logger<AsyncFullLineRequest>(FullLinePart.NETWORK)

    fun requestAllAsync(
      providers: List<FullLineCompletionProvider>,
      query: FullLineCompletionQuery,
      head: String
    ): AsyncFullLineRequest {
      val queue = LinkedBlockingQueue<RequestResult>()
      val indicator = ProgressManager.getInstance().progressIndicator
      val startTime = System.currentTimeMillis()
      providers.forEach { provider ->
        runAsync {
          val result = provider.getVariants(query, indicator)

          result.forEach {
            it.details.provider = provider.getId()
            it.details.inferenceTime = (System.currentTimeMillis() - startTime).toInt()
          }

          return@runAsync result.map { it.withSuggestion(head + it.suggestion) }
        }
          .onSuccess { queue.offer(RequestResult(provider.getId(), it, true)) }
          .onError {
            when (it) {
              is TimeoutException, is ProcessCanceledException, is CancellationException -> Unit
              else -> LOG.error(it)
            }
            queue.offer(RequestResult(provider.getId(), emptyList(), true))
          }
      }

      return AsyncFullLineRequest(providers.size, queue)
    }
  }

  private data class RequestResult(
    val providerId: String,
    val proposals: List<RawFullLineProposal>,
    val isLast: Boolean
  )
}
