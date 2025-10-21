package com.intellij.grazie.cloud

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.cloud.license.GrazieLoginManager
import com.intellij.grazie.cloud.license.GrazieLoginState
import com.intellij.grazie.cloud.license.ensureTermsOfServiceShown
import com.intellij.grazie.cloud.license.notifyOnLicensingError
import com.intellij.grazie.utils.runBlockingModalProcess
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

private val logger = logger<DefaultGrazieCloudConnector>()

class DefaultGrazieCloudConnector : GrazieCloudConnector {
  override fun isAuthorized(): Boolean = GrazieLoginManager.state().value.isCloudConnected

  override fun connect(project: Project): Boolean = runBlockingModalProcess(project, title = GrazieBundle.message("grazie.cloud.logging.in.progress.title")) {
    val state = GrazieLoginManager.logInToCloud(true)
    logger.debug { "State returned from the cloud login routine: $state" }
    return@runBlockingModalProcess when (state) {
      is GrazieLoginState.Cloud -> true
      is GrazieLoginState.Jba -> {
        state.error?.let { notifyOnLicensingError(it) }
        false
      }
      is GrazieLoginState.Enterprise -> {
        val hasErrors = state.state.error != null
        if (hasErrors) {
          notifyOnLicensingError(state.state.error)
        }
        !hasErrors
      }
      else -> false
    }
  }

  override fun isCloudEnabledByDefault(): Boolean = false

  override fun askUserConsentForCloud(): Boolean = ensureTermsOfServiceShown()

  override fun api(): SuspendableAPIGatewayClient = GrazieCloudConfig.api()

  override fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit): Unit =
    GrazieLoginManager.subscribe(disposable, listener)
}