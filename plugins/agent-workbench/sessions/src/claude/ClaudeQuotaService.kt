// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.claude

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<ClaudeQuotaService>()
private const val KEYCHAIN_SERVICE = "Claude Code-credentials"
private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
private const val PROCESS_TIMEOUT_MS = 5_000
private const val CRED_TYPE_GENERIC = 1
private val POLL_INTERVAL = 1.minutes

@Service(Service.Level.APP)
internal class ClaudeQuotaService(private val serviceScope: CoroutineScope) {
  private val refreshMutex = Mutex()
  private val pollingStarted = AtomicBoolean(false)
  private val jsonFactory = JsonFactory()
  private val mutableState = MutableStateFlow(ClaudeQuotaState())
  val state: StateFlow<ClaudeQuotaState> = mutableState.asStateFlow()

  fun startPolling() {
    if (!pollingStarted.compareAndSet(false, true)) return
    serviceScope.launch(Dispatchers.IO) {
      while (true) {
        refresh()
        delay(POLL_INTERVAL)
      }
    }
  }

  fun requestRefresh() {
    serviceScope.launch(Dispatchers.IO) { refresh() }
  }

  private suspend fun refresh() {
    if (!refreshMutex.tryLock()) return
    try {
      mutableState.value = mutableState.value.copy(isLoading = true, error = null)
      val token = withContext(Dispatchers.IO) { readOAuthToken() }
      if (token == null) {
        mutableState.value = ClaudeQuotaState(error = ClaudeQuotaError.NO_CREDENTIALS)
        return
      }
      val result = withContext(Dispatchers.IO) { fetchUsage(token) }
      mutableState.value = result
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to fetch Claude quota", e)
      mutableState.value = ClaudeQuotaState(error = ClaudeQuotaError.UNKNOWN)
    }
    finally {
      refreshMutex.unlock()
    }
  }

  private fun readOAuthToken(): String? {
    readTokenFromKeychain()?.let { return it }
    return readTokenFromCredentialsFile()
  }

  private fun readTokenFromKeychain(): String? {
    return when {
      SystemInfo.isMac -> readTokenFromMacKeychain()
      SystemInfo.isWindows -> readTokenFromWindowsCredentialManager()
      else -> null
    }
  }

  private fun readTokenFromMacKeychain(): String? {
    return try {
      val commandLine = GeneralCommandLine("security", "find-generic-password", "-s", KEYCHAIN_SERVICE, "-w")
      val handler = CapturingProcessHandler(commandLine)
      val result = handler.runProcess(PROCESS_TIMEOUT_MS)
      if (result.isTimeout || result.exitCode != 0) return null
      val raw = result.stdout.trim()
      if (raw.isEmpty()) return null
      extractTokenFromJson(raw)
    }
    catch (e: Throwable) {
      LOG.debug("Failed to read macOS keychain credentials", e)
      null
    }
  }

  private fun readTokenFromWindowsCredentialManager(): String? {
    return try {
      val pCred = PointerByReference()
      if (!WinCredLib.INSTANCE.CredReadW(WString(KEYCHAIN_SERVICE), CRED_TYPE_GENERIC, 0, pCred)) return null
      try {
        val cred = WinCredential(pCred.value)
        cred.read()
        if (cred.CredentialBlobSize <= 0 || cred.CredentialBlob == null) return null
        val raw = cred.CredentialBlob!!.getByteArray(0, cred.CredentialBlobSize)
        val json = String(raw, Charsets.UTF_16LE)
        extractTokenFromJson(json)
      }
      finally {
        WinCredLib.INSTANCE.CredFree(pCred.value)
      }
    }
    catch (e: Throwable) {
      LOG.debug("Failed to read Windows Credential Manager", e)
      null
    }
  }

  private fun readTokenFromCredentialsFile(): String? {
    return try {
      val credentialsPath = Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
      if (!credentialsPath.exists()) return null
      extractTokenFromJson(credentialsPath.readText())
    }
    catch (e: Throwable) {
      LOG.debug("Failed to read credentials file", e)
      null
    }
  }

  private fun extractTokenFromJson(raw: String): String? {
    return try {
      jsonFactory.createParser(raw).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          val fieldName = parser.currentName()
          parser.nextToken()
          if (fieldName == "claudeAiOauth" && parser.currentToken == JsonToken.START_OBJECT) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
              val innerField = parser.currentName()
              parser.nextToken()
              if (innerField == "accessToken" && parser.currentToken == JsonToken.VALUE_STRING) {
                return parser.text
              }
              parser.skipChildren()
            }
          }
          else {
            parser.skipChildren()
          }
        }
        null
      }
    }
    catch (e: Throwable) {
      LOG.debug("Failed to parse credentials JSON", e)
      null
    }
  }

  private fun fetchUsage(token: String): ClaudeQuotaState {
    val client = HttpClient.newBuilder().build()
    val request = HttpRequest.newBuilder()
      .uri(URI.create(USAGE_URL))
      .header("Accept", "application/json")
      .header("Authorization", "Bearer $token")
      .header("anthropic-beta", "oauth-2025-04-20")
      .GET()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      return ClaudeQuotaState(error = ClaudeQuotaError.AUTH_FAILED)
    }
    if (response.statusCode() !in 200..299) {
      LOG.warn("Claude usage API returned status ${response.statusCode()}")
      return ClaudeQuotaState(error = ClaudeQuotaError.NETWORK_ERROR)
    }
    return try {
      parseUsageResponse(response.body())
    }
    catch (e: Throwable) {
      LOG.warn("Failed to parse Claude usage response", e)
      ClaudeQuotaState(error = ClaudeQuotaError.UNKNOWN)
    }
  }

  private fun parseUsageResponse(body: String): ClaudeQuotaState {
    var fiveHourPercent: Int? = null
    var fiveHourReset: Long? = null
    var sevenDayPercent: Int? = null
    var sevenDayReset: Long? = null

    jsonFactory.createParser(body).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return ClaudeQuotaState(error = ClaudeQuotaError.UNKNOWN)
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        parser.nextToken()
        when (fieldName) {
          "five_hour" -> {
            val bucket = parseBucket(parser)
            if (bucket != null) {
              fiveHourPercent = bucket.first
              fiveHourReset = bucket.second
            }
          }
          "seven_day" -> {
            val bucket = parseBucket(parser)
            if (bucket != null) {
              sevenDayPercent = bucket.first
              sevenDayReset = bucket.second
            }
          }
          else -> parser.skipChildren()
        }
      }
    }
    val info = ClaudeQuotaInfo(
      fiveHourPercent = fiveHourPercent,
      fiveHourReset = fiveHourReset,
      sevenDayPercent = sevenDayPercent,
      sevenDayReset = sevenDayReset,
    )
    return ClaudeQuotaState(quotaInfo = info)
  }

}

private fun parseBucket(parser: JsonParser): Pair<Int?, Long?>? {
  if (parser.currentToken == JsonToken.VALUE_NULL) return null
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var utilization: Int? = null
  var resetMillis: Long? = null
  while (parser.nextToken() != JsonToken.END_OBJECT) {
    val field = parser.currentName()
    parser.nextToken()
    when (field) {
      "utilization" -> {
        if (parser.currentToken == JsonToken.VALUE_NUMBER_INT || parser.currentToken == JsonToken.VALUE_NUMBER_FLOAT) {
          utilization = parser.intValue
        }
      }
      "resets_at" -> {
        if (parser.currentToken == JsonToken.VALUE_STRING) {
          resetMillis = try {
            Instant.parse(parser.text).toEpochMilli()
          }
          catch (_: Throwable) {
            null
          }
        }
      }
      else -> parser.skipChildren()
    }
  }
  return Pair(utilization, resetMillis)
}

@Suppress("FunctionName")
private interface WinCredLib : StdCallLibrary {
  companion object {
    val INSTANCE: WinCredLib by lazy { Native.load("advapi32", WinCredLib::class.java) }
  }

  fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
  fun CredFree(credential: Pointer)
}

@Suppress("PropertyName", "unused")
@Structure.FieldOrder(
  "Flags", "Type", "TargetName", "Comment", "LastWritten",
  "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount",
  "Attributes", "TargetAlias", "UserName",
)
private class WinCredential(p: Pointer) : Structure(p) {
  @JvmField var Flags: Int = 0
  @JvmField var Type: Int = 0
  @JvmField var TargetName: WString? = null
  @JvmField var Comment: WString? = null
  @JvmField var LastWritten: Long = 0
  @JvmField var CredentialBlobSize: Int = 0
  @JvmField var CredentialBlob: Pointer? = null
  @JvmField var Persist: Int = 0
  @JvmField var AttributeCount: Int = 0
  @JvmField var Attributes: Pointer? = null
  @JvmField var TargetAlias: WString? = null
  @JvmField var UserName: WString? = null
}
