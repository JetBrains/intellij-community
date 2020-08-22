// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil.getInactiveTextColor
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHOAuthService
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.Validator
import java.util.concurrent.TimeoutException
import javax.swing.JComponent

internal class GHOAuthCredentialsUi(
  val factory: GithubApiRequestExecutor.Factory,
  val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  override fun getPreferredFocusableComponent(): JComponent? = null

  override fun getValidator(): Validator = { null }

  override fun createExecutor(): GithubApiRequestExecutor = factory.create("")

  override fun acquireLoginAndToken(
    server: GithubServerPath,
    executor: GithubApiRequestExecutor,
    indicator: ProgressIndicator
  ): Pair<String, String> {
    executor as GithubApiRequestExecutor.WithTokenAuth

    val token = acquireToken(indicator)
    executor.token = token

    val login = GHTokenCredentialsUi.acquireLogin(server, executor, indicator, isAccountUnique, null)
    return login to token
  }

  override fun handleAcquireError(error: Throwable): ValidationInfo = GHTokenCredentialsUi.handleError(error)

  override fun setBusy(busy: Boolean) = Unit

  override fun LayoutBuilder.centerPanel() {
    row {
      val progressLabel = JBLabel(message("label.login.progress")).apply {
        icon = AnimatedIcon.Default()
        foreground = getInactiveTextColor()
      }
      progressLabel()
    }
  }

  private fun acquireToken(indicator: ProgressIndicator): String {
    val tokenPromise = GHOAuthService.instance.requestToken()
    try {
      return tokenPromise.blockingGet(indicator)
    }
    catch (e: ProcessCanceledException) {
      tokenPromise.cancel()
      throw e
    }
  }

  private fun CancellablePromise<String>.blockingGet(indicator: ProgressIndicator): String {
    while (true) {
      checkCancelledEvenWithPCEDisabled(indicator)
      try {
        return blockingGet(1000) ?: throw ProcessCanceledException()
      }
      catch (ignored: TimeoutException) {
      }
    }
  }
}