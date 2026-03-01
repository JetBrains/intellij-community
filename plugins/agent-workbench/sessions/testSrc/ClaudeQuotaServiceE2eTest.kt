// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaError
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaInfo
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaState
import com.intellij.agent.workbench.sessions.claude.formatWidgetText
import com.intellij.agent.workbench.sessions.claude.formatWidgetTooltip
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText

private const val RUN_CLAUDE_QUOTA_E2E_PROPERTY = "agent.workbench.sessions.runClaudeQuotaE2e"

/**
 * End-to-end test for the Claude quota pipeline.
 * Disabled by default; set -Dagent.workbench.sessions.runClaudeQuotaE2e=true to enable.
 * Requires real Claude credentials (keychain or ~/.claude/.credentials.json).
 */
class ClaudeQuotaServiceE2eTest {
  private val jsonFactory = JsonFactory()

  @Test
  fun fullPipelineReturnsValidQuotaState() {
    assumeTrue("Set -D$RUN_CLAUDE_QUOTA_E2E_PROPERTY=true to run E2E test", java.lang.Boolean.getBoolean(RUN_CLAUDE_QUOTA_E2E_PROPERTY))
    val token = readOAuthToken()
    assumeTrue("No Claude OAuth credentials found — skipping E2E test", token != null)

    val response = fetchUsageRaw(token!!)
    assertThat(response.statusCode())
      .describedAs("Usage API HTTP status")
      .isIn(200, 201)

    val body = response.body()
    assertThat(body).describedAs("Response body").isNotEmpty()

    val state = parseUsageResponse(body)
    assertThat(state.error)
      .describedAs("Parsing should not produce an error")
      .isNull()
    assertThat(state.quotaInfo)
      .describedAs("Parsing should produce quota info")
      .isNotNull()

    val info = state.quotaInfo!!
    val soft = SoftAssertions()

    // At least one bucket should have a non-null utilization
    soft.assertThat(info.fiveHourPercent != null || info.sevenDayPercent != null)
      .describedAs("At least one utilization value must be present")
      .isTrue()

    if (info.fiveHourPercent != null) {
      soft.assertThat(info.fiveHourPercent).describedAs("5h percent").isBetween(0, 100)
      soft.assertThat(info.fiveHourReset).describedAs("5h reset").isNotNull()
      soft.assertThat(info.fiveHourReset).describedAs("5h reset is in the future").isGreaterThan(System.currentTimeMillis())
    }
    if (info.sevenDayPercent != null) {
      soft.assertThat(info.sevenDayPercent).describedAs("7d percent").isBetween(0, 100)
      soft.assertThat(info.sevenDayReset).describedAs("7d reset").isNotNull()
      soft.assertThat(info.sevenDayReset).describedAs("7d reset is in the future").isGreaterThan(System.currentTimeMillis())
    }

    soft.assertAll()

    // Verify the formatting functions don't crash and produce non-empty output
    val text = formatWidgetText(info)
    assertThat(text).describedAs("Widget text").isNotEmpty()

    val tooltip = formatWidgetTooltip(info, System.currentTimeMillis())
    assertThat(tooltip).describedAs("Widget tooltip").isNotEmpty()
  }

  @Test
  fun utilizationFieldParsedAsFloat() {
    // The API returns utilization as a float (e.g. 48.0), not an integer.
    // This test verifies our parser handles that correctly.
    val json = """{"five_hour":{"utilization":48.0,"resets_at":"2099-01-01T00:00:00+00:00"},"seven_day":{"utilization":1.0,"resets_at":"2099-01-01T00:00:00+00:00"}}"""
    val state = parseUsageResponse(json)

    assertThat(state.error).isNull()
    assertThat(state.quotaInfo).isNotNull()
    val floatInfo = state.quotaInfo!!
    assertThat(floatInfo.fiveHourPercent).isEqualTo(48)
    assertThat(floatInfo.sevenDayPercent).isEqualTo(1)
  }

  @Test
  fun utilizationFieldParsedAsInt() {
    val json = """{"five_hour":{"utilization":48,"resets_at":"2099-01-01T00:00:00+00:00"},"seven_day":{"utilization":1,"resets_at":"2099-01-01T00:00:00+00:00"}}"""
    val state = parseUsageResponse(json)

    assertThat(state.error).isNull()
    assertThat(state.quotaInfo).isNotNull()
    val intInfo = state.quotaInfo!!
    assertThat(intInfo.fiveHourPercent).isEqualTo(48)
    assertThat(intInfo.sevenDayPercent).isEqualTo(1)
  }

  @Test
  fun nullBucketsHandledGracefully() {
    val json = """{"five_hour":null,"seven_day":null,"extra_usage":{"is_enabled":false}}"""
    val state = parseUsageResponse(json)

    assertThat(state.error).isNull()
    assertThat(state.quotaInfo).isNotNull()
    val nullInfo = state.quotaInfo!!
    assertThat(nullInfo.fiveHourPercent).isNull()
    assertThat(nullInfo.sevenDayPercent).isNull()
  }

  @Test
  fun unknownFieldsSkipped() {
    val json = """{"five_hour":{"utilization":10.0,"resets_at":"2099-01-01T00:00:00+00:00"},"seven_day_opus":null,"seven_day_sonnet":{"utilization":3.0,"resets_at":"2099-01-01T00:00:00+00:00"},"iguana_necktie":null,"extra_usage":{"is_enabled":false,"monthly_limit":null}}"""
    val state = parseUsageResponse(json)

    assertThat(state.error).isNull()
    assertThat(state.quotaInfo).isNotNull()
    assertThat(state.quotaInfo!!.fiveHourPercent).isEqualTo(10)
  }

  // --- Helpers (duplicated from ClaudeQuotaService to test parsing independently) ---

  private fun readOAuthToken(): String? {
    readTokenFromKeychain()?.let { return it }
    return readTokenFromCredentialsFile()
  }

  private fun readTokenFromKeychain(): String? {
    return try {
      val commandLine = GeneralCommandLine("security", "find-generic-password", "-s", "Claude Code-credentials", "-w")
      val handler = CapturingProcessHandler(commandLine)
      val result = handler.runProcess(5_000)
      if (result.isTimeout || result.exitCode != 0) return null
      val raw = result.stdout.trim()
      if (raw.isEmpty()) return null
      extractTokenFromJson(raw)
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun readTokenFromCredentialsFile(): String? {
    return try {
      val credentialsPath = Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
      if (!credentialsPath.exists()) return null
      extractTokenFromJson(credentialsPath.readText())
    }
    catch (_: Throwable) {
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
    catch (_: Throwable) {
      null
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
    return ClaudeQuotaState(
      quotaInfo = ClaudeQuotaInfo(
        fiveHourPercent = fiveHourPercent,
        fiveHourReset = fiveHourReset,
        sevenDayPercent = sevenDayPercent,
        sevenDayReset = sevenDayReset,
      ),
    )
  }

}

private fun fetchUsageRaw(token: String): HttpResponse<String> {
  val client = HttpClient.newBuilder().build()
  val request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.anthropic.com/api/oauth/usage"))
    .header("Accept", "application/json")
    .header("Authorization", "Bearer $token")
    .header("anthropic-beta", "oauth-2025-04-20")
    .GET()
    .build()
  return client.send(request, HttpResponse.BodyHandlers.ofString())
}

private fun parseBucket(parser: com.fasterxml.jackson.core.JsonParser): Pair<Int?, Long?>? {
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
