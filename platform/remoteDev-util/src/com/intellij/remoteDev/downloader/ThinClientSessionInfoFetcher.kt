package com.intellij.remoteDev.downloader

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.connection.CodeWithMeSessionInfoProvider
import com.intellij.remoteDev.connection.StunTurnServerInfo
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.withFragment
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * Lightweight implementation of LobbyServerAPI to avoid obfuscation issues
 */
@ApiStatus.Experimental
object ThinClientSessionInfoFetcher {

  private val objectMapper = lazy { ObjectMapper() }
  private val logger by lazy { logger<ThinClientSessionInfoFetcher>() }

  private fun getErrorPayload(connection: HttpURLConnection): ByteArray {
    val errorStream = connection.errorStream ?: throw IOException("Connection errorStream is null")
    val stream = if (connection.contentEncoding == "gzip") GZIPInputStream(errorStream) else errorStream
    return stream.use { it.readAllBytes() }
  }

  fun getSessionUrl(joinLinkUrl: URI): CodeWithMeSessionInfoProvider {
    val url = createUrl(joinLinkUrl)
    val requestString = objectMapper.value.createObjectNode().apply {
      put("clientBuildNumber", currentBuildNumber())
      put("clientPlatform", currentPlatform())
    }.toPrettyString()

    return HttpRequests.post(url, HttpRequests.JSON_CONTENT_TYPE)
      .throwStatusCodeException(false)
      .productNameAsUserAgent()
      .tuner {
        it.setRequestProperty("Accept", HttpRequests.JSON_CONTENT_TYPE)
      }
      .connect { request ->
        request.write(requestString)

        val connection = request.connection as HttpURLConnection
        val jsonResponseString = try {
          request.readString()
        } catch (ioException: IOException) {
          val errorPayload = getErrorPayload(connection)
          String(errorPayload, Charsets.UTF_8)
        }

        if (connection.responseCode == 403 || connection.responseCode == 451) {
          try {
            val sessionInfo = objectMapper.value.reader().readTree(jsonResponseString)
            if (sessionInfo["messageId"]?.textValue() == "FORBIDDEN_BY_REGION_RESTRICTION") {
              val learnMoreLink = sessionInfo["learnMoreLink"]?.textValue()
              val message = sessionInfo["message"]?.textValue() ?: "Forbidden"
              val reason = sessionInfo["forbiddenReasonText"]?.textValue()
              val allTogetherText = StringBuilder()
                .append(message)
              if (learnMoreLink != null) {
                allTogetherText.append("\n" + learnMoreLink)
              }
              if (reason != null) {
                allTogetherText.append("\n" + reason)
              }
              // todo: dialog
              throw Exception(allTogetherText.toString())
            }
          } catch (ex: JacksonException) {
            logger.warn("Failed to decode response", ex)
          }
        }
        if (connection.responseCode >= 400) {
          error("Request to $url failed with status code ${connection.responseCode}")
        }

        val sessionInfo = objectMapper.value.reader().readTree(jsonResponseString)
        val jreUrlNode = sessionInfo["compatibleJreUrl"]
        return@connect object : CodeWithMeSessionInfoProvider {
          override val hostBuildNumber = sessionInfo["hostBuildNumber"].asText()
          override val compatibleClientUrl = sessionInfo["compatibleClientUrl"].asText()
          override val isUnattendedMode = false
          override val compatibleJreUrl = if (jreUrlNode.isNull) null else jreUrlNode.asText()
          override val hostFeaturesToEnable: Set<String>
            get() = throw UnsupportedOperationException("hostFeaturesToEnable field should not be used")
          override val stunTurnServers: List<StunTurnServerInfo>
            get() = throw UnsupportedOperationException("stunTurnServers field should not be used")
          override val downloadPgpPublicKeyUrl: String? = sessionInfo["downloadPgpPublicKeyUrl"]?.asText()
        }
      }
  }

  private fun createUrl(joinLinkUrl: URI): String {
    val baseLink = joinLinkUrl.withFragment(null).toString().trimEnd('/')
    return "$baseLink/info"
  }

  private fun currentBuildNumber(): String {
    return ApplicationInfo.getInstance().build.toString()
  }

  private fun currentPlatform(): String {
    return when {
      SystemInfo.isMac && CpuArch.isArm64() -> "osx-aarch64"
      SystemInfo.isMac && CpuArch.isIntel64() -> "osx-x64"
      SystemInfo.isLinux && CpuArch.isArm64() -> "linux-aarch64"
      SystemInfo.isLinux && CpuArch.isIntel64() -> "linux-x64"
      SystemInfo.isWindows && CpuArch.isIntel64() -> "windows-x64"
      SystemInfo.isWindows && CpuArch.isArm64() -> "windows-aarch64"
      else -> error("Unsupported OS type: ${SystemInfo.OS_NAME}, CpuArch: ${CpuArch.CURRENT}")
    }
  }
}