// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.IOException
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

private const val DATASET_PATH: String = "platform/searchEverywhere/frontend/testData/actionsRanker/actions-nl-dataset.yaml"
private const val PROGRESS_STEP: Int = 50
private val MAX_RETRY_DELAY: Duration = 1.minutes
private val HIT_POSITIONS: List<Int> = listOf(1, 3, 5, 10)

/** The miss table lists the requests without an expected action in this number of first results. */
private const val MISS_POSITION: Int = 5
private const val SHOWN_RESULTS: Int = 3

/**
 * The evaluation dataset of natural-language action search.
 *
 * @property ide the product and the build of the IDE that the [actions] come from.
 * @property actions the candidate actions with the template texts.
 */
internal class SeRankerDataset(val ide: String, val actions: List<SeActionsRankerCandidate>, val requests: List<SeRankerDatasetRequest>) {
  companion object {
    /** The dataset in the repository. */
    fun defaultPath(): Path = Path.of(PlatformTestUtil.getCommunityPath(), DATASET_PATH)

    fun load(path: Path): SeRankerDataset {
      val root = Files.newBufferedReader(path).use { Yaml(SafeConstructor(LoaderOptions())).load<Map<String, Any?>>(it) }
      val ide = root.getValue("ide") as Map<*, *>
      val actions = (root.getValue("actions") as List<*>).map { entry ->
        entry as Map<*, *>
        SeActionsRankerCandidate(entry["id"] as String, entry["name"] as String, entry["description"] as String?)
      }
      val requests = (root.getValue("requests") as List<*>).map { entry ->
        entry as Map<*, *>
        SeRankerDatasetRequest(entry["request"] as String, (entry["expected"] as List<*>).map { it as String }, entry["source"] as String)
      }
      return SeRankerDataset("${ide["product"]} ${ide["build"]}", actions, requests)
    }
  }
}

/**
 * One request of [SeRankerDataset].
 *
 * @property expected the ids of 1 to 3 actions that solve the request, the best first.
 * @property source the id of the action that the request was written for.
 */
internal class SeRankerDatasetRequest(val request: String, val expected: List<String>, val source: String)

/** A decision API ranker under evaluation: the [api] at [url] with the [model] and the personal [key]. */
internal class SeEvaluatedRanker(val name: String, val api: SeDecisionsApi, val url: String, val model: String, val key: String) {
  override fun toString(): String = "$name with $model at $url"
}

/**
 * The ranking of one request by one ranker.
 *
 * @property matches the ids of the ranked actions, the best first, or `null` when each attempt failed.
 * @property rankingDuration the time of the [SeActionsCandidatesStrategy] call of the successful attempt.
 * @property requestDurations the time of each HTTP request of the successful attempt.
 * @property throttledResponses the number of responses with HTTP 429 before the successful attempt.
 */
internal class SeRankerRequestResult(
  val request: SeRankerDatasetRequest,
  val matches: List<String>?,
  val rankingDuration: Duration?,
  val requestDurations: List<Duration>,
  val throttledResponses: Int,
) {
  /** The position of the first expected action in [matches], from 1, or `null` when no expected action is among them. */
  val expectedPosition: Int?
    get() = matches?.indexOfFirst { it in request.expected }?.takeIf { it >= 0 }?.plus(1)
}

/** The evaluation of one ranker. [rejection] tells why the evaluation stopped early, for example a rejected key. */
internal class SeRankerRun(val ranker: SeEvaluatedRanker, val results: List<SeRankerRequestResult>, val rejection: String?)

/**
 * Ranks the requests with a [SeEvaluatedRanker] through [SeDecisionsActionsRankerSession], as Search Everywhere does.
 * Each ranking sends all [candidates] with [SeActionsCandidatesStrategy.SPLIT_AND_MERGE], without the debounce of the popup.
 *
 * Each attempt opens a new session, because a session sends no more requests after a refusal.
 * The session sends its requests through a wrapper of [client] that records the time and the status of each request.
 * - HTTP 429 gets a new attempt after [retryDelay].
 * - HTTP 401 and 403 stop the evaluation of the ranker.
 * - An IO failure, HTTP 5xx, or an answer that does not decode get a new attempt after [retryDelay]. Other statuses fail the request.
 */
internal class SeRankerEvaluator(
  private val client: HttpClient,
  private val candidates: List<SeActionsRankerCandidate>,
  private val concurrency: Int,
  private val maxAttempts: Int = 6,
  private val retryDelay: (attempt: Int, retryAfter: Duration?) -> Duration = ::defaultRetryDelay,
  private val progress: (String) -> Unit = {},
) {
  suspend fun evaluate(ranker: SeEvaluatedRanker, requests: List<SeRankerDatasetRequest>): SeRankerRun = coroutineScope {
    val semaphore = Semaphore(concurrency)
    val rejection = AtomicReference<String?>()
    val done = AtomicInteger()
    val results = requests.map { request ->
      async {
        semaphore.withPermit {
          if (rejection.get() != null) return@withPermit null
          val result = rank(ranker, request, rejection, this@coroutineScope)
          val count = done.incrementAndGet()
          if (count % PROGRESS_STEP == 0 || count == requests.size) progress("${ranker.name}: $count of ${requests.size} requests")
          result
        }
      }
    }.awaitAll()
    SeRankerRun(ranker, results.filterNotNull(), rejection.get())
  }

  /** Returns the ranking of [request], or `null` when the ranker rejects the key. */
  private suspend fun rank(
    ranker: SeEvaluatedRanker,
    request: SeRankerDatasetRequest,
    rejection: AtomicReference<String?>,
    scope: CoroutineScope,
  ): SeRankerRequestResult? {
    val query = request.request.trim()
    var throttledResponses = 0
    for (attempt in 0 until maxAttempts) {
      val timedClient = SeTimedHttpClient(client)
      val session = SeDecisionsActionsRankerSession(SeEvaluationLease(ranker), ranker.model, timedClient, scope, ranker.api)
      val (matches, duration) = try {
        measureTimedValue { SeActionsCandidatesStrategy.SPLIT_AND_MERGE.rank(session, query, candidates) }
      }
      finally {
        Disposer.dispose(session)
      }

      val calls = timedClient.calls.toList()
      val rejected = calls.firstOrNull { it.statusCode == 401 || it.statusCode == 403 }
      if (rejected != null) {
        rejection.compareAndSet(null, "The ${ranker.name} API rejected the key with HTTP ${rejected.statusCode}.")
        return null
      }
      val throttled = calls.filter { it.statusCode == 429 }
      if (throttled.isNotEmpty()) {
        throttledResponses += throttled.size
        delay(retryDelay(attempt, throttled.mapNotNull { it.retryAfter }.maxOrNull()))
        continue
      }
      if (calls.all { it.isAnswer(ranker.api) }) {
        return SeRankerRequestResult(request, matches.map { it.id }, duration, calls.map { it.duration }, throttledResponses)
      }
      if (calls.none { it.isRetryable(ranker.api) }) break
      delay(retryDelay(attempt, null))
    }
    return SeRankerRequestResult(request, matches = null, rankingDuration = null, requestDurations = emptyList(), throttledResponses)
  }
}

/** Waits as long as the API asks, and at least 2 seconds that double with each attempt, up to [MAX_RETRY_DELAY]. */
private fun defaultRetryDelay(attempt: Int, retryAfter: Duration?): Duration {
  return maxOf(retryAfter ?: Duration.ZERO, (2L shl attempt).seconds).coerceAtMost(MAX_RETRY_DELAY)
}

/** The endpoint of [ranker] with its personal key. It does not print the key, because the session logs the endpoint. */
private class SeEvaluationLease(private val ranker: SeEvaluatedRanker) : SeDecisionsEndpointLease {
  override val url: String get() = ranker.url
  override val headers: Map<String, String> get() = mapOf("Authorization" to "Bearer ${ranker.key}")

  override suspend fun close() {}

  override fun toString(): String = "the evaluation endpoint $url"
}

/**
 * One HTTP request of an attempt.
 *
 * @property statusCode the HTTP status, or `null` after an IO failure.
 * @property retryAfter the wait that the `Retry-After` header asks for.
 */
private class SeTimedCall(val duration: Duration, val statusCode: Int?, val body: String?, val retryAfter: Duration?) {
  fun isAnswer(api: SeDecisionsApi): Boolean = isSuccess() && decodes(api)

  fun isRetryable(api: SeDecisionsApi): Boolean {
    return when {
      statusCode == null -> true
      statusCode >= 500 -> true
      else -> isSuccess() && !decodes(api)
    }
  }

  private fun isSuccess(): Boolean = statusCode != null && statusCode in 200..299

  private fun decodes(api: SeDecisionsApi): Boolean {
    val body = body ?: return false
    return try {
      api.decodeAnswer(body) != null
    }
    catch (_: Exception) {
      false
    }
  }
}

/** Sends the requests through [delegate] and records the time and the status of each [send] call. The session uses only [send]. */
private class SeTimedHttpClient(private val delegate: HttpClient) : HttpClient() {
  val calls: ConcurrentLinkedQueue<SeTimedCall> = ConcurrentLinkedQueue()

  override fun <T> send(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
    val start = TimeSource.Monotonic.markNow()
    val response = try {
      delegate.send(request, responseBodyHandler)
    }
    catch (e: IOException) {
      calls += SeTimedCall(start.elapsedNow(), statusCode = null, body = null, retryAfter = null)
      throw e
    }
    val duration = start.elapsedNow()
    val retryAfter = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()?.seconds
    calls += SeTimedCall(duration, response.statusCode(), response.body() as? String, retryAfter)
    return response
  }

  override fun <T> sendAsync(
    request: HttpRequest,
    responseBodyHandler: HttpResponse.BodyHandler<T>,
  ): CompletableFuture<HttpResponse<T>> = delegate.sendAsync(request, responseBodyHandler)

  override fun <T> sendAsync(
    request: HttpRequest,
    responseBodyHandler: HttpResponse.BodyHandler<T>,
    pushPromiseHandler: HttpResponse.PushPromiseHandler<T>?,
  ): CompletableFuture<HttpResponse<T>> = delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler)

  override fun cookieHandler(): Optional<CookieHandler> = delegate.cookieHandler()
  override fun connectTimeout(): Optional<java.time.Duration> = delegate.connectTimeout()
  override fun followRedirects(): Redirect = delegate.followRedirects()
  override fun proxy(): Optional<ProxySelector> = delegate.proxy()
  override fun sslContext(): SSLContext = delegate.sslContext()
  override fun sslParameters(): SSLParameters = delegate.sslParameters()
  override fun authenticator(): Optional<Authenticator> = delegate.authenticator()
  override fun version(): Version = delegate.version()
  override fun executor(): Optional<Executor> = delegate.executor()
}

/** The quality and the time metrics of one [SeRankerRun]. Only the ranked requests count, the failed requests do not. */
internal class SeRankerMetrics(run: SeRankerRun) {
  val ranked: List<SeRankerRequestResult> = run.results.filter { it.matches != null }
  val failed: Int = run.results.size - ranked.size
  val throttledResponses: Int = run.results.sumOf { it.throttledResponses }
  val bestFirst: Int = ranked.count { it.matches.orEmpty().firstOrNull() == it.request.expected.first() }
  val empty: Int = ranked.count { it.matches.orEmpty().isEmpty() }
  val meanReciprocalRank: Double = ranked.meanOf { result -> result.expectedPosition?.let { 1.0 / it } ?: 0.0 }
  val meanResultCount: Double = ranked.meanOf { it.matches.orEmpty().size.toDouble() }
  val meanRequestsPerRanking: Double = ranked.meanOf { it.requestDurations.size.toDouble() }
  val requestDurations: List<Duration> = ranked.flatMap { it.requestDurations }.sorted()
  val rankingDurations: List<Duration> = ranked.mapNotNull { it.rankingDuration }.sorted()

  /** The number of the ranked requests with an expected action in the first [k] results. */
  fun hits(k: Int): Int = ranked.count { result -> result.expectedPosition?.let { it <= k } == true }
}

private fun <T> List<T>.meanOf(selector: (T) -> Double): Double = if (isEmpty()) 0.0 else sumOf(selector) / size

private fun List<Duration>.mean(): Duration? = if (isEmpty()) null else reduce(Duration::plus) / size

/** Returns the nearest-rank [percent] percentile of the sorted durations, or `null` when there are none. */
internal fun List<Duration>.percentile(percent: Int): Duration? {
  if (isEmpty()) return null
  return this[(Math.ceilDiv(size * percent, 100) - 1).coerceIn(0, lastIndex)]
}

/**
 * Renders the Markdown report of the [runs] on the [dataset].
 *
 * @param settings the evaluation settings that the report lists, such as the date and the concurrency.
 */
internal fun renderRankerReport(dataset: SeRankerDataset, settings: Map<String, String>, runs: List<SeRankerRun>): String = buildString {
  val metrics = runs.map { SeRankerMetrics(it) }
  val header = listOf("Metric") + runs.map { it.ranker.name }

  appendLine("# Search Everywhere actions ranker evaluation")
  appendLine()
  for ((name, value) in settings) appendLine("- $name: $value")
  appendLine("- Dataset: ${dataset.actions.size} actions of ${dataset.ide}, ${dataset.requests.size} requests")
  for (run in runs) {
    appendLine("- ${run.ranker.name}: the model `${run.ranker.model}` at ${run.ranker.url}")
    run.rejection?.let { appendLine("  - The evaluation stopped early. $it") }
  }
  appendLine()

  appendLine("## Ranking quality")
  appendLine()
  appendLine("Hit@k is the share of the ranked requests with an expected action in the first k results. " +
             "The interval in brackets is the 95% Wilson score interval.")
  appendLine()
  table(header, buildList {
    add(listOf("Ranked requests") + metrics.map { "${it.ranked.size}" })
    add(listOf("Failed requests") + metrics.map { "${it.failed}" })
    for (k in HIT_POSITIONS) add(listOf("Hit@$k") + metrics.map { share(it.hits(k), it.ranked.size, interval = true) })
    add(listOf("Best expected action first") + metrics.map { share(it.bestFirst, it.ranked.size, interval = true) })
    add(listOf("Mean reciprocal rank") + metrics.map { format("%.3f", it.meanReciprocalRank) })
    add(listOf("No result") + metrics.map { share(it.empty, it.ranked.size, interval = false) })
    add(listOf("Mean result count") + metrics.map { format("%.1f", it.meanResultCount) })
  })

  appendLine("## Request duration")
  appendLine()
  appendLine("The time of one HTTP request, from the start of the send to the full response. Only the requests of the ranked requests count. " +
             "The throttled attempts, the waits before a new attempt, and the debounce of Search Everywhere do not count.")
  appendLine()
  table(header, durationRows(metrics) { it.requestDurations } + listOf(
    listOf("Requests per ranking") + metrics.map { format("%.2f", it.meanRequestsPerRanking) },
    listOf("Throttled responses") + metrics.map { "${it.throttledResponses}" },
  ))

  appendLine("## Ranking duration")
  appendLine()
  appendLine("The time of one ranking with all its requests: the parallel requests of the parts, then the final request.")
  appendLine()
  table(header, durationRows(metrics) { it.rankingDurations })

  if (runs.size == 2) appendHeadToHead(runs, metrics)
  appendMisses(dataset, runs)
}

private fun durationRows(metrics: List<SeRankerMetrics>, durations: (SeRankerMetrics) -> List<Duration>): List<List<String>> {
  return listOf(
    listOf("Count") + metrics.map { "${durations(it).size}" },
    listOf("Mean") + metrics.map { milliseconds(durations(it).mean()) },
    listOf("Median") + metrics.map { milliseconds(durations(it).percentile(50)) },
    listOf("p90") + metrics.map { milliseconds(durations(it).percentile(90)) },
    listOf("p95") + metrics.map { milliseconds(durations(it).percentile(95)) },
    listOf("Max") + metrics.map { milliseconds(durations(it).lastOrNull()) },
  )
}

private fun StringBuilder.appendHeadToHead(runs: List<SeRankerRun>, metrics: List<SeRankerMetrics>) {
  val (first, second) = runs.map { it.ranker.name }
  val secondResults = metrics[1].ranked.associateBy { it.request }
  val pairs = metrics[0].ranked.mapNotNull { result -> secondResults[result.request]?.let { result to it } }
  fun found(result: SeRankerRequestResult) = result.expectedPosition?.let { it <= MISS_POSITION } == true
  fun position(result: SeRankerRequestResult) = result.expectedPosition ?: Int.MAX_VALUE

  appendLine("## Head to head")
  appendLine()
  appendLine("The ${pairs.size} requests that both rankers ranked.")
  appendLine()
  table(listOf("Outcome", "Requests"), listOf(
    listOf("Both find an expected action in the first $MISS_POSITION results", "${pairs.count { (a, b) -> found(a) && found(b) }}"),
    listOf("Only $first finds it", "${pairs.count { (a, b) -> found(a) && !found(b) }}"),
    listOf("Only $second finds it", "${pairs.count { (a, b) -> !found(a) && found(b) }}"),
    listOf("Neither finds it", "${pairs.count { (a, b) -> !found(a) && !found(b) }}"),
    listOf("$first puts an expected action higher", "${pairs.count { (a, b) -> position(a) < position(b) }}"),
    listOf("$second puts an expected action higher", "${pairs.count { (a, b) -> position(a) > position(b) }}"),
  ))
}

private fun StringBuilder.appendMisses(dataset: SeRankerDataset, runs: List<SeRankerRun>) {
  val resultsByRun = runs.map { run -> run.results.associateBy { it.request } }
  val misses = dataset.requests.filter { request ->
    resultsByRun.any { results -> results[request]?.let { (it.expectedPosition ?: Int.MAX_VALUE) > MISS_POSITION } == true }
  }

  appendLine("## Misses")
  appendLine()
  appendLine("The ${misses.size} requests for which a ranker has no expected action in the first $MISS_POSITION results. " +
             "Each ranker column shows its first $SHOWN_RESULTS results.")
  appendLine()
  table(listOf("Request", "Expected") + runs.map { it.ranker.name }, misses.map { request ->
    listOf(request.request, request.expected.joinToString(", ")) + resultsByRun.map { results ->
      val result = results[request]
      when {
        result == null -> "not ranked"
        result.matches == null -> "failed"
        result.matches.isEmpty() -> "no result"
        else -> result.matches.take(SHOWN_RESULTS).joinToString(", ")
      }
    }
  })
}

private fun StringBuilder.table(header: List<String>, rows: List<List<String>>) {
  appendLine(header.joinToString(" | ", "| ", " |") { cell(it) })
  appendLine(header.joinToString(" | ", "| ", " |") { "---" })
  for (row in rows) appendLine(row.joinToString(" | ", "| ", " |") { cell(it) })
  appendLine()
}

private fun cell(text: String): String = text.replace("|", "\\|").replace('\n', ' ')

private fun share(count: Int, total: Int, interval: Boolean): String {
  if (total == 0) return "-"
  val percent = format("%.1f%%", 100.0 * count / total)
  if (!interval) return percent
  val (low, high) = wilsonInterval(count, total)
  return "$percent (${format("%.1f", 100 * low)}-${format("%.1f", 100 * high)})"
}

/** Returns the 95% Wilson score interval of the share [count] of [total]. */
private fun wilsonInterval(count: Int, total: Int): Pair<Double, Double> {
  val z = 1.96
  val p = count.toDouble() / total
  val denominator = 1 + z * z / total
  val center = (p + z * z / (2 * total)) / denominator
  val margin = z * sqrt(p * (1 - p) / total + z * z / (4.0 * total * total)) / denominator
  return (center - margin).coerceAtLeast(0.0) to (center + margin).coerceAtMost(1.0)
}

private fun milliseconds(duration: Duration?): String = duration?.let { "${it.inWholeMilliseconds} ms" } ?: "-"

private fun format(pattern: String, value: Double): String = String.format(Locale.ROOT, pattern, value)
