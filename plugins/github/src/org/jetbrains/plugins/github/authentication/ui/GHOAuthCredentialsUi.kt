// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHOAuthService
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.Validator
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal class GHOAuthCredentialsUi(
  val factory: GithubApiRequestExecutor.Factory,
  val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  override fun getPreferredFocusableComponent(): JComponent? = null

  override fun getValidator(): Validator = { null }

  override fun submitLoginTask(server: GithubServerPath, indicator: ProgressIndicator): CompletableFuture<Pair<String, String>> =
    service<ProgressManager>().submitIOTask(indicator) {
      val token = acquireToken(indicator)
      val executor = factory.create(token)
      val login = GHTokenCredentialsUi.acquireLogin(server, executor, indicator, isAccountUnique, null)
      login to token
    }

  override fun handleAcquireError(error: Throwable): ValidationInfo = GHTokenCredentialsUi.handleError(error)

  override fun setBusy(busy: Boolean) = Unit

  override fun Panel.centerPanel() {
    row {
      label(message("label.login.progress")).applyToComponent {
        icon = AnimatedIcon.Default()
        foreground = NamedColorUtil.getInactiveTextColor()
      }
    }
  }

  private fun acquireToken(indicator: ProgressIndicator): String {
    val credentialsFuture = GHOAuthService.instance.authorize()
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture, indicator).accessToken
    }
    catch (pce: ProcessCanceledException) {
      credentialsFuture.completeExceptionally(pce)
      throw pce
    }
  }
}