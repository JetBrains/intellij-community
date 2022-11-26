package org.jetbrains.completion.full.line.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.providers.CloudFullLineCompletionProvider
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.services.managers.missedLanguage
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState.FLVerificationStatus
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask

class FullLineStartupActivity : StartupActivity {
  /**
   * Activity running only if token is provided, but plugin is not verified (for ex, when updating plugin from below 0.2.53 version)
   */
  override fun runActivity(project: Project) {
    if (MLServerCompletionSettings.availableLanguages.isEmpty()) return

    checkDeprecatedVerificationStatus()

    checkAndDownloadLocalModels()
  }

  private fun checkDeprecatedVerificationStatus() {
    if (MlServerCompletionAuthState.getInstance().state.verified != FLVerificationStatus.UNKNOWN) return

    if (MlServerCompletionAuthState.getInstance().authToken().isEmpty()) {
      MlServerCompletionAuthState.getInstance().state.verified = FLVerificationStatus.UNVERIFIED
    }
    else {
      CloudFullLineCompletionProvider.checkStatus(
        MLServerCompletionSettings.availableLanguages.first(),
        MlServerCompletionAuthState.getInstance().authToken()
      ).onSuccess {
        MlServerCompletionAuthState.getInstance().state.verified = FLVerificationStatus.fromStatusCode(it)
      }.onError {
        MlServerCompletionAuthState.getInstance().state.verified = FLVerificationStatus.UNVERIFIED
      }
    }
  }

  private fun checkAndDownloadLocalModels() {
    val settings = MLServerCompletionSettings.getInstance()
    if (settings.getModelMode() == ModelType.Cloud) return

    val actions = MLServerCompletionSettings.availableLanguages.filter {
      service<ConfigurableModelsManager>().missedLanguage(it)
    }.map {
      SetupLocalModelsTask.ToDoParams(it, SetupLocalModelsTask.Action.DOWNLOAD)
    }

    if (actions.isNotEmpty()) {
      SetupLocalModelsTask.queue(actions)
    }
  }
}
