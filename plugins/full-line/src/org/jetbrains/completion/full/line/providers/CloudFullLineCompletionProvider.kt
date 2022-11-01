package org.jetbrains.completion.full.line.providers

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.lang.Language
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.completion.full.line.FullLineCompletionMode
import org.jetbrains.completion.full.line.FullLineProposal
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.awaitWithCheckCanceled
import org.jetbrains.completion.full.line.language.KeepKind
import org.jetbrains.completion.full.line.models.RequestError
import org.jetbrains.completion.full.line.platform.FullLineCompletionQuery
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.platform.diagnostics.logger
import org.jetbrains.completion.full.line.settings.FullLineNotifications
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class CloudFullLineCompletionProvider : FullLineCompletionProvider {
  override fun getId(): String = "server"

  override fun getVariants(query: FullLineCompletionQuery, indicator: ProgressIndicator): List<RawFullLineProposal> {
    if (MlServerCompletionAuthState.getInstance().state.authToken.isBlank()) {
      FullLineNotifications.Cloud.showAuthorizationError(query.project)
      return emptyList()
    }
    val start = System.currentTimeMillis()
    val future = submitQuery(query)
    return future.awaitWithCheckCanceled(start, Registry.get("full.line.server.host.max.latency").asInteger(), indicator)
      .also { LOG.info("Time to predict completions is ${(System.currentTimeMillis() - start) / 1000.0} sec") }
      .map { it.asRaw() }
      .also { emulateServerDelay(start) }
  }

  internal fun submitQuery(query: FullLineCompletionQuery): Future<List<ServerFullLineProposition>> {
    return executor.submit(Callable {
      try {
        HttpRequests.post("${getServer(query.language).asString()}/v1/complete/gpt", "application/json")
          .connect { r ->
            r.connection.setRequestProperty("Authorization", MlServerCompletionAuthState.getInstance().state.authToken)
            val request = FullLineCompletionServerRequest.fromSettings(query)


            r.write(GSON.toJson(request))

            if ((r.connection as HttpURLConnection).responseCode == 403) {
              FullLineNotifications.Cloud.showAuthorizationError(query.project)

              emptyList()
            }
            else {
              val raw = r.reader.readText()

              GSON.fromJson(raw, ServerFullLineResult::class.java)?.completions
              ?: logError(GSON.fromJson(raw, RequestError::class.java))
            }
          }
      }
      catch (e: Exception) {
        val message = when (e) {
          is ConnectException -> return@Callable emptyList()
          is SocketTimeoutException -> "Timeout. Probably IP is wrong"
          is HttpRequests.HttpStatusException -> "Something wrong with completion server"
          is ExecutionException, is IllegalStateException -> "Error while getting completions from server"
          else -> "Some other error occurred"
        }
        logError(message, e)
      }
    })
  }

  private fun ServerFullLineProposition.asRaw(): RawFullLineProposal {
    return RawFullLineProposal(
      suggestion,
      score,
      FullLineProposal.BasicSyntaxCorrectness.fromBoolean(isSyntaxCorrect)
    )
  }

  private fun logError(msg: String, throwable: Throwable): List<ServerFullLineProposition> {
    LOG.debug(msg, throwable)
    return emptyList()
  }

  private fun logError(error: RequestError): List<ServerFullLineProposition> = logError("Server bad response", error)

  private fun emulateServerDelay(start: Long) {
    val registryValue = Registry.get("full.line.server.emulate.model.response")
    if (registryValue.isChangedFromDefault) {
      val delay = registryValue.asInteger()
      val waitFor = delay - (System.currentTimeMillis() - start)
      if (waitFor > 0) {
        LOG.info("Emulating server delay for next $waitFor ms...")
        Thread.sleep(waitFor)
        LOG.info("Emulating server delay finished")
      }
    }
  }

  companion object {
    private val GSON = Gson()
    private val LOG = logger<CloudFullLineCompletionProvider>(FullLinePart.NETWORK)

    private const val timeout = 3L

    //Completion server cancels all threads besides the last one
    //We need at least 2 threads, the second one must (almost) instantly cancel the first one, otherwise, UI will freeze
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ML Server Completion", 2)

    /**
     * return Response status for connection with server or throws an error if it can't connect to server
     * for specific authentication token
     *  - 200 - Success
     *  - 403 - Forbidden
     *  - 500 - Server error
     */
    fun checkStatus(language: Language, authToken: String): Promise<Int> {
      LOG.info("Getting ${language.id} server status")

      val promise = executor.submitAndGetPromise {
        val serverReg = getServer(language)

        try {
          val myURLConnection = URL("${serverReg.asString()}/v1/status").openConnection() as HttpURLConnection
          myURLConnection.apply {
            setRequestProperty("Authorization", authToken)
            requestMethod = "GET"
          }.let {
            it.disconnect()
            it.responseCode
          }
        }
        catch (e: Throwable) {
          throw if (serverReg.isChangedFromDefault) CloudExceptionWithCustomRegistry(e, serverReg) else e
        }
      }
      ProgressManager.checkCanceled()
      return promise
    }

    @Suppress("UnresolvedPluginConfigReference")
    private fun getServer(language: Language) = Registry.get(
      when (MLServerCompletionSettings.getInstance().getModelState(language).psiBased) {
        false -> "full.line.server.host.${language.id.toLowerCase()}"
        true -> "full.line.server.host.psi.${language.id.toLowerCase()}"
      }
    )
  }

  private data class ServerFullLineResult(val completions: List<ServerFullLineProposition>)

  // Set default `isSyntaxCorrect` to null since the server doesn't support and may not to return this feature yet
  internal data class ServerFullLineProposition(
    val score: Double,
    val suggestion: String,
    val isSyntaxCorrect: Boolean? = null
  )

  private data class FullLineCompletionServerRequest(
    val code: String,
    @SerializedName("prefix")
    val token: String,
    val offset: Int,
    val filename: String,
    val language: String,

    val mode: FullLineCompletionMode,

    @SerializedName("num_iterations")
    val numIterations: Int,
    @SerializedName("beam_size")
    val beamSize: Int,
    @SerializedName("diversity_groups")
    val diversityGroups: Int,
    @SerializedName("diversity_strength")
    val diversityStrength: Double,
    @SerializedName("len_norm_base")
    val lenNormBase: Double,
    @SerializedName("len_norm_pow")
    val lenNormPow: Double,

    @SerializedName("top_n")
    val topN: Int?,
    @SerializedName("group_top_n")
    val groupTopN: Int?,
    @SerializedName("only_full_lines")
    val onlyFullLines: Boolean,

    val model: String?,
    @SerializedName("group_answers")
    val groupAnswers: Boolean?,
    @SerializedName("context_len")
    val contextLength: Int,
    @SerializedName("started_ts")
    val startedTs: Long,
    @SerializedName("min_prefix_dist")
    val minimumPrefixDist: Double?,
    @SerializedName("min_edit_dist")
    val minimumEditDist: Double?,
    @SerializedName("keep_kinds")
    val keepKinds: Set<KeepKind>?,
    @SerializedName("rollback_prefix")
    val rollbackPrefix: List<String>,
  ) {
    companion object {
      fun fromSettings(
        query: FullLineCompletionQuery,
        settings: MLServerCompletionSettings = MLServerCompletionSettings.getInstance(),
      ): FullLineCompletionServerRequest {
        val langState = settings.getLangState(query.language)
        val modelState = settings.getModelState(langState)
        return with(query) {
          FullLineCompletionServerRequest(
            context,
            prefix,
            offset,
            filename,
            language.displayName,
            mode,
            modelState.numIterations,
            modelState.beamSize,
            modelState.diversityGroups,
            modelState.diversityStrength,
            modelState.lenBase,
            modelState.lenPow,
            settings.topN(),
            settings.groupTopN(language),
            langState.onlyFullLines,
            null,
            langState.groupAnswers,
            modelState.contextLength(),
            System.currentTimeMillis(),
            modelState.minimumPrefixDist,
            modelState.minimumEditDist,
            modelState.keepKinds,
            rollbackPrefix,
          )
        }
      }
    }
  }
}

class CloudExceptionWithCustomRegistry(cause: Throwable, val registry: RegistryValue) : Throwable(cause)

fun <T> ExecutorService.submitAndGetPromise(callable: () -> T): Promise<T> {
  val promise = AsyncPromise<T>()
  execute {
    val result = try {
      callable()
    }
    catch (e: Throwable) {
      promise.setError(e)
      return@execute
    }
    promise.setResult(result)
  }
  return promise
}
