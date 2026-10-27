// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.io.HttpRequests.HttpStatusException
import com.intellij.util.net.PlatformHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val MODEL_REGISTRY_KEY: String = "search.everywhere.actions.ranker.decisions.model"
internal const val DECISIONS_DEFAULT_MODEL: String = "gpt-6-luna"

private const val NO_ACTION_DESCRIPTION: String = "No IDE action does what the user asks for."
private const val CANDIDATE_VALUE_PREFIX: String = "a"
private const val MAX_DESCRIPTION_LENGTH: Int = 200
private const val MAX_LOGGED_ERROR_BODY_LENGTH: Int = 1000

/** After one of these statuses, the session sends no more requests: a rejected credential, or a rate or a quota limit. */
private val REFUSAL_STATUS_CODES: Set<Int> = setOf(401, 403, 429)

/** The ranker drops the candidates whose probability is lower. */
private const val MIN_PROBABILITY: Double = 0.01

private val LOG = logger<SeDecisionsActionsRanker>()

/**
 * Orders the actions with the OpenAI Decision API.
 *
 * The ranker holds no token. Each session uses the endpoint of [acquireDecisionsEndpoint] while the popup is open.
 * The session asks one `choice` question. Each candidate is one choice, and one more choice says that no action matches.
 * The session orders the candidates by the probability of their choices.
 *
 * The `search.everywhere.actions.ranker.decisions.model` registry key names the model.
 * To select this ranker, set the `search.everywhere.actions.ranker` registry key to `decisions`.
 */
internal class SeDecisionsActionsRanker : SeActionsRanker {
  override val id: String get() = "decisions"
  override val diagnosticName: String get() = "Decision API"

  override suspend fun openSession(): SeActionsRankerSession? {
    val model = RegistryManager.getInstanceAsync().stringValue(MODEL_REGISTRY_KEY)?.trim()?.ifEmpty { null } ?: DECISIONS_DEFAULT_MODEL
    val service = serviceAsync<SeDecisionsActionsRankerService>()

    // No suspension follows the acquisition, thus a cancellation cannot lose an acquired lease.
    val lease = acquireDecisionsEndpoint() ?: return null
    return SeDecisionsActionsRankerSession(lease, model, service.client, service.scope, SeDecisionsApi.OPENAI)
  }
}

/**
 * Owns the HTTP client of the decision API rankers, and the scope that closes the endpoint leases.
 */
@Service(Service.Level.APP)
internal class SeDecisionsActionsRankerService(val scope: CoroutineScope) : Disposable {
  val client: HttpClient = PlatformHttpClient.client()

  override fun dispose() {
    client.shutdownNow()
  }
}

/**
 * The ranking of one Search Everywhere popup with one typed decision [api]. It holds the endpoint [lease] until [dispose].
 *
 * [dispose] closes the lease in [scope], because a lease closes in a coroutine and a disposal cannot suspend.
 * After the endpoint refuses a request with a status of [REFUSAL_STATUS_CODES], the session tells the lease once and sends no more requests.
 */
internal class SeDecisionsActionsRankerSession(
  private val lease: SeDecisionsEndpointLease,
  private val model: String,
  private val client: HttpClient,
  private val scope: CoroutineScope,
  private val api: SeDecisionsApi = SeDecisionsApi.OPENAI,
) : SeActionsRankerSession {
  private val failureReported = AtomicBoolean()
  private val isRefused = AtomicBoolean()

  /** One choice of the question is the choice that says that no action matches. */
  override val maxCandidates: Int get() = api.maxChoices - 1

  override suspend fun rank(query: String, candidates: List<SeActionsRankerCandidate>): List<SeActionsRankerMatch> {
    if (isRefused.get()) return emptyList()

    val idsByValue = HashMap<String, String>(candidates.size)
    val choices = ArrayList<SeDecisionsChoice>(candidates.size + 1)
    for ((index, candidate) in candidates.withIndex()) {
      // The value is a short key, because the decision APIs document no allowed characters, and an action id can contain any.
      val value = "$CANDIDATE_VALUE_PREFIX$index"
      idsByValue[value] = candidate.id
      choices.add(SeDecisionsChoice(value, choiceDescription(candidate)))
    }
    // A choice question needs at least two choices. This choice also lets the model say that nothing matches.
    choices.add(SeDecisionsChoice(NO_ACTION_VALUE, NO_ACTION_DESCRIPTION))

    LOG.debug { "Rank ${candidates.size} candidates with '$model' through $lease" }
    val answer = send(api.encodeRequest(model, query, choices)) ?: return emptyList()
    val choice = answer.choice
    if (choice == null) {
      LOG.debug { "The ${api.displayName} answered with no choice. A refusal has no choice." }
      return emptyList()
    }

    val probabilities = answer.probabilities
    if (probabilities == null) {
      return listOfNotNull(idsByValue[choice]?.let { SeActionsRankerMatch(it, probability = null) })
    }
    return probabilities.entries.asSequence()
      .filter { (_, probability) -> probability >= MIN_PROBABILITY }
      .sortedByDescending { (_, probability) -> probability }
      .mapNotNull { (value, probability) -> idsByValue[value]?.let { SeActionsRankerMatch(it, probability) } }
      .toList()
  }

  override fun dispose() {
    scope.launch {
      try {
        lease.close()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.warn("Could not close $lease", e)
      }
    }
  }

  /**
   * Sends the [json] request to the operation of [api] at the endpoint, and returns the answer.
   * Returns `null` when the request fails, or when the response has no answer to the question.
   *
   * A cancellation interrupts the thread, and the HTTP client then cancels the request.
   */
  private suspend fun send(json: String): SeDecisionsAnswer? {
    val httpRequest = buildHttpRequest(json) ?: return null

    // PlatformHttpClient.send throws for an error status and drops the body, which names the reason of the gateway.
    val errorBody = AtomicReference<String>()
    val bodyHandler = HttpResponse.BodyHandler { info ->
      HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)) { body ->
        if (info.statusCode() !in 200..299) errorBody.set(body)
        body
      }
    }

    return try {
      val body = runInterruptible(Dispatchers.IO) {
        PlatformHttpClient.send(client, httpRequest, bodyHandler)
      }
      api.decodeAnswer(body)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      val statusCode = (e as? HttpStatusException)?.statusCode
      if (statusCode != null && statusCode in REFUSAL_STATUS_CODES && isRefused.compareAndSet(false, true)) {
        tellRefused(statusCode)
      }
      val status = statusCode?.let { " with the HTTP status $it" }.orEmpty()
      val details = errorBody.get()?.takeIf { it.isNotBlank() }?.let { ": ${it.take(MAX_LOGGED_ERROR_BODY_LENGTH).replace('\n', ' ')}" }.orEmpty()
      reportFailure("The ${api.displayName} request through $lease failed$status$details", e)
      null
    }
  }

  /**
   * Builds the HTTP request with the [json] body, or returns `null` when the endpoint has a URL or a header that is not valid.
   *
   * The report omits the exception, because the exception of a header that is not valid quotes the header value.
   * A header can carry a credential.
   */
  private fun buildHttpRequest(json: String): HttpRequest? {
    return try {
      PlatformHttpClient.requestBuilder(URI.create(api.operationUrl(lease.url)))
        .header("Content-Type", "application/json")
        .apply { lease.headers.forEach { (name, value) -> setHeader(name, value) } }
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build()
    }
    catch (_: IllegalArgumentException) {
      reportFailure("The ${api.displayName} request through $lease has a URL or a header that is not valid", cause = null)
      null
    }
  }

  /** Tells the lease about the refusal. A failure of the lease owner does not stop the ranking. */
  private fun tellRefused(statusCode: Int) {
    try {
      lease.refused(statusCode)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("$lease could not handle the HTTP status $statusCode", e)
    }
  }

  /**
   * Reports the first failure of the session as a warning, and the next ones on the debug level.
   *
   * A failing endpoint fails each query, thus a warning for each query would flood the log.
   */
  private fun reportFailure(message: String, cause: Exception?) {
    if (failureReported.compareAndSet(false, true)) {
      LOG.warn(message, cause)
    }
    else {
      LOG.debug(cause) { message }
    }
  }
}

private fun choiceDescription(candidate: SeActionsRankerCandidate): String {
  val description = candidate.description?.takeIf { it.isNotBlank() } ?: return candidate.name.take(MAX_DESCRIPTION_LENGTH)
  return "${candidate.name}: $description".take(MAX_DESCRIPTION_LENGTH)
}
