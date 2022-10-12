package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.lang.Language
import com.intellij.ui.components.JBPasswordField
import org.jetbrains.completion.full.line.providers.CloudFullLineCompletionProvider
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import javax.swing.JButton

fun pingButton(language: Language, loadingIcon: LoadingComponent, authTokenTextField: JBPasswordField?): JButton {
  return JButton(message("fl.server.completion.connection")).apply {
    addActionListener {
      val t1 = System.currentTimeMillis()
      val token = authTokenTextField?.let { String(it.password) } ?: MlServerCompletionAuthState.getInstance().state.authToken
      loadingIcon.withAsyncProgress(
        CloudFullLineCompletionProvider.checkStatus(language, token).then {
          val latency = System.currentTimeMillis() - t1
          val state = if (it == 200) LoadingComponent.State.SUCCESS else LoadingComponent.State.ERROR
          val msg = when (it) {
            200 -> "Successful with ${latency}ms delay"
            403 -> "Auth key is incorrect with ${latency}ms delay"
            else -> "Status failed with code $this and ${latency}ms delay"
          }
          state to msg
        }
      )
    }
  }
}
