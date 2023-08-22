package com.intellij.remoteDev.util

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@NlsSafe
const val jetbrains_gateway_protocol_name = "jetbrains-gateway"

@ApiStatus.Experimental
object RemoteDevProtocolUtil {
  const val gatewayPrefix = "$jetbrains_gateway_protocol_name://connect"
  const val httpPrefix = "https://code-with-me.jetbrains.com/remoteDev"

  fun createGatewayUrl(parameters: Map<String, String>): String {
    return "$gatewayPrefix#" + buildParamsString(parameters)
  }

  fun createHttpUrl(parameters: Map<String, String>): String {
    return "$httpPrefix#" + buildParamsString(parameters)
  }

  private fun urlEncode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
  }

  private fun urlDecode(value: String): String {
    return URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
  }

  private fun buildParamsString(params: Map<String, String>): String {
    return params.map { "${it.key}=${urlEncode(it.value)}" }.joinToString("&")
  }

  fun parseParamsString(params: String): Map<String, String> {
    val parameters = mutableMapOf<String, String>()
    for (parameterPair in params.split("&")) {
      val index = parameterPair.indexOf("=")
      if (index == -1) parameters[parameterPair] = ""
      else parameters[parameterPair.substring(0, index)] = urlDecode(parameterPair.substring(index + 1))
    }
    return parameters
  }
}
