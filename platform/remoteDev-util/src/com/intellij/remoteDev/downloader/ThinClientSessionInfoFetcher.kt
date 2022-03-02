package com.intellij.remoteDev.downloader

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.remoteDev.connection.CodeWithMeSessionInfoProvider
import com.intellij.remoteDev.connection.StunTurnServerInfo
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.withFragment
import org.jetbrains.annotations.ApiStatus
import java.net.HttpURLConnection
import java.net.URI

/**
 * Lightweight implementation of LobbyServerAPI to avoid obfuscation issues
 */
@ApiStatus.Experimental
object ThinClientSessionInfoFetcher {

  private val objectMapper = lazy { ObjectMapper() }

  fun getSessionUrl(joinLinkUrl: URI): CodeWithMeSessionInfoProvider {
    val url = createUrl(joinLinkUrl)
    val requestString = objectMapper.value.createObjectNode().apply {
      put("clientBuildNumber", currentBuildNumber())
      put("clientPlatform", currentPlatform())
    }.toPrettyString()

    return HttpRequests.post(url, HttpRequests.JSON_CONTENT_TYPE)
      .throwStatusCodeException(false)
      .productNameAsUserAgent()
      .connect { request ->
        request.write(requestString)

        val connection = request.connection as HttpURLConnection
        val responseString = request.readString()

        if (connection.responseCode >= 400) {
          throw Exception("Request to $url failed with status code ${connection.responseCode}")
        }

        val sessionInfo = objectMapper.value.reader().readTree(responseString)
        return@connect object : CodeWithMeSessionInfoProvider {
          override val hostBuildNumber = sessionInfo["hostBuildNumber"].asText()
          override val compatibleClientName = sessionInfo["compatibleClientName"].asText()
          override val compatibleClientUrl = sessionInfo["compatibleClientUrl"].asText()
          override val compatibleJreName = sessionInfo["compatibleJreName"].asText()
          override val isUnattendedMode = false
          override val compatibleJreUrl = sessionInfo["compatibleJreUrl"].asText()
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
      SystemInfo.isLinux && CpuArch.isIntel64() -> "linux-x64"
      SystemInfo.isWindows && CpuArch.isIntel64() -> "windows-x64"
      else -> error("Unsupported OS type: ${SystemInfo.OS_NAME}, CpuArch: ${CpuArch.CURRENT}")
    }
  }
}