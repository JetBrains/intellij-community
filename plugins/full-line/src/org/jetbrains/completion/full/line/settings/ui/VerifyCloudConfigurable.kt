package org.jetbrains.completion.full.line.settings.ui

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.completion.full.line.providers.CloudFullLineCompletionProvider
import org.jetbrains.completion.full.line.settings.FullLineNotifications
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState.FLVerificationStatus
import org.jetbrains.completion.full.line.settings.ui.components.LoadingComponent
import org.jetbrains.completion.full.line.settings.ui.components.loadingStatus
import javax.swing.JButton

class VerifyCloudConfigurable : BoundConfigurable(message("fl.server.completion.display")) {
  private val settings = MlServerCompletionAuthState.getInstance()
  private lateinit var authTokenField: JBPasswordField

  override fun createPanel(): DialogPanel {
    return if (MLServerCompletionSettings.availableLanguages.isEmpty()) {
      noLanguagePanel()
    }
    else {
      verifyTokenPanel()
    }
  }

  private fun noLanguagePanel(): DialogPanel {
    return panel {
      row {
        text("You have no supported languages.")
      }
    }
  }

  private fun verifyTokenPanel(): DialogPanel {
    return panel {
      row(message("fl.server.completion.auth")) {
        authTokenField = passwordField()
          .columns(25)
          .bindText(settings.state::authToken)
          .onApply { FullLineNotifications.Cloud.clearAuthError() }
          .component
      }
      row {
        comment(message("fl.server.eap.comment"), maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row {
        val loadingIcon = LoadingComponent()
        lateinit var button: JButton
        button = button(message("fl.server.eap.verify")) {
          loadingIcon.withAsyncProgress(
            CloudFullLineCompletionProvider.checkStatus(
              MLServerCompletionSettings.availableLanguages.first(),
              String(authTokenField.password)
            ).then {
              val state = if (it == 200) {
                LoadingComponent.State.SUCCESS.also { settings.state.verified = FLVerificationStatus.VERIFIED }
              }
              else {
                LoadingComponent.State.ERROR.also { settings.state.verified = FLVerificationStatus.UNVERIFIED }
              }
              val msg = when (it) {
                200 -> "Successful"
                403 -> "Auth key is incorrect"
                else -> "Status failed with code $this"
              }
              button.putClientProperty(
                AUTH_TOKEN_UPDATE,
                !(button.getClientProperty(AUTH_TOKEN_UPDATE) as Boolean)
              )
              state to msg
            }
          )
        }.applyToComponent {
          putClientProperty(AUTH_TOKEN_UPDATE, getClientProperty(AUTH_TOKEN_UPDATE) ?: false)
        }.component

        if (settings.isVerified()) {
          button.doClick()
        }
        loadingStatus(loadingIcon)
      }
    }
  }

  override fun getHelpTopic() = "full.line.completion.verify"
}
