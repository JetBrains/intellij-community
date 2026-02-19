package com.intellij.grazie.cloud

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.client.common.SuspendableHTTPClient
import ai.grazie.client.common.WithCloudAuth
import ai.grazie.client.common.auth.GrazieAgents
import ai.grazie.client.common.model.RequestOptions
import ai.grazie.model.auth.GrazieAgent
import ai.grazie.model.auth.v5.AuthData
import ai.grazie.model.cloud.AuthType
import ai.grazie.model.cloud.AuthVersion
import ai.grazie.model.cloud.HeaderCollection
import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.of
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.cloud.license.EnterpriseState
import com.intellij.grazie.cloud.license.GrazieLoginManager
import com.intellij.grazie.cloud.license.GrazieLoginState
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application

object GrazieCloudConfig {
  private const val STGN_URL = "https://api.app.stgn.grazie.aws.intellij.net"

  private val apiUrl: String
    get() {
      if (application.isUnitTestMode) return STGN_URL

      return when (Registry.stringValue("grazie.cloud.stage")) {
        "PROD" -> Registry.stringValue("grazie.cloud.production.url")
        "STGN" -> STGN_URL
        else -> error("Unknown stage for Grazie Cloud -- should be either STGN or PROD")
      }
    }

  private fun obtainAgent(): GrazieAgent {
    val version = ApplicationInfo.getInstance().getBuild().baselineVersion.toString()
    return when {
      application.isUnitTestMode -> GrazieAgent("grazie-professional-test-plugin", version)
      else -> GrazieAgents.Plugin.Professional(version)
    }
  }

  fun api(httpClient: SuspendableHTTPClient = service<GrazieHttpClientManager>().instance): SuspendableAPIGatewayClient {
    val loginState = GrazieLoginManager.lastState
    if (loginState is GrazieLoginState.Enterprise) {
      return enterpriseClient(loginState.state, httpClient)
    }

    val v5 = SuspendableHTTPClient.WithV5(
      client = httpClient,
      authData = AuthData(
        token = (loginState as? GrazieLoginState.Cloud)?.grazieToken.orEmpty(),
        originalUserToken = null,
        originalServiceToken = null,
        originalApplicationToken = null,
        grazieAgent = obtainAgent()
      )
    )
    return SuspendableAPIGatewayClient(
      apiUrl,
      v5,
      if (System.getenv("TEAMCITY_VERSION") != null) AuthType.Application else AuthType.User
    )
  }

  fun enterpriseClient(state: EnterpriseState, httpClient: SuspendableHTTPClient): SuspendableAPIGatewayClient {
    val headers = HeaderCollection.of(state.headers.mapValues { listOf(it.value) })
    val wrapper = object : SuspendableHTTPClient.WithHeaders(httpClient, headers), WithCloudAuth {
      override val authVersion: AuthVersion = AuthVersion.V5

      override suspend fun appendHeaders(options: RequestOptions): RequestOptions {
        if (!state.isAIEnterpriseConnected()) {
          throw HTTPConnectionError(GrazieBundle.message("ai.enterprise.error.message", state.error.orEmpty()))
        }
        return super.appendHeaders(options)
      }
    }
    return SuspendableAPIGatewayClient(state.endpoint ?: "no AI Enterprise server", wrapper, AuthType.User)
  }
}