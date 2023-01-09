// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHOAuthService
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.Validator
import java.util.concurrent.CancellationException
import javax.swing.JComponent

internal class GHOAuthCredentialsUi(
  val factory: GithubApiRequestExecutor.Factory,
  val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  override fun getPreferredFocusableComponent(): JComponent? = null

  override fun getValidator(): Validator = { null }

  override suspend fun login(server: GithubServerPath): Pair<String, String> {
    val token = acquireToken()
    val executor = factory.create(token)
    val login = GHTokenCredentialsUi.acquireLogin(server, executor, isAccountUnique, null)
    return login to token
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

  private suspend fun acquireToken(): String {
    val credentialsFuture = GHOAuthService.instance.authorize()
    try {
      return credentialsFuture.await().accessToken
    }
    catch (ce: CancellationException) {
      credentialsFuture.completeExceptionally(ProcessCanceledException(ce))
      throw ce
    }
  }
}