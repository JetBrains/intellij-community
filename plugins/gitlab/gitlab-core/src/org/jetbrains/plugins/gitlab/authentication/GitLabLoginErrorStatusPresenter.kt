// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.net.ConnectException
import javax.swing.Action

internal class GitLabLoginErrorStatusPresenter(
  private val cs: CoroutineScope,
  private val model: GitLabTokenLoginPanelModel,
) : ErrorStatusPresenter.HTML<Throwable> {
  override fun getErrorTitle(error: Throwable): String = ""

  override fun getHTMLBody(error: Throwable): @NlsSafe String {
    val builder = HtmlBuilder()
    when (error) {
      is ConnectException -> builder.append(CollaborationToolsBundle.message("clone.dialog.login.error.server"))
      is LoginException.UnsupportedServerVersion -> builder.customizeUnsupportedVersionError(error)
      is LoginException.InvalidTokenOrUnsupportedServerVersion -> builder.customizeInvalidTokenOrUnsupportedServerVersionError(error)
      is LoginException.AccountAlreadyExists -> builder.append(CollaborationToolsBundle.message("login.dialog.error.account.already.exists", error.username))
      is LoginException.AccountUsernameMismatch -> builder.append(CollaborationToolsBundle.message("login.dialog.error.account.username.mismatch", error.requiredUsername, error.username))
      else -> builder.append(ExceptionUtil.getPresentableMessage(error))
    }

    return builder.wrapWithHtmlBody().toString()
  }

  override fun getErrorAction(error: Throwable): Action? = when (error) {
    is LoginException.UnsupportedServerVersion,
    is LoginException.InvalidTokenOrUnsupportedServerVersion -> swingAction(CollaborationToolsBundle.message("login.via.git")) {
      cs.launch {
        model.tryGitAuthorization()
      }
    }
    else -> null
  }

  private fun HtmlBuilder.customizeUnsupportedVersionError(
    error: LoginException.UnsupportedServerVersion
  ): HtmlBuilder {
    val builder = this
    val text = GitLabBundle.message("server.version.unsupported", error.earliestSupportedVersion)
    val link = HtmlChunk.link(ErrorStatusPresenter.ERROR_ACTION_HREF, CollaborationToolsBundle.message("login.via.git"))

    return builder
      .append(text).nbsp()
      .append(link)
  }

  private fun HtmlBuilder.customizeInvalidTokenOrUnsupportedServerVersionError(
    error: LoginException.InvalidTokenOrUnsupportedServerVersion
  ): HtmlBuilder {
    val builder = this
    val text = GitLabBundle.message("invalid.token.or.server.version.unsupported", error.earliestSupportedVersion)
    val linkAction = HtmlChunk.link(ErrorStatusPresenter.ERROR_ACTION_HREF, CollaborationToolsBundle.message("login.via.git"))
    val linkAdditionalText = GitLabBundle.message("invalid.token.or.server.version.unsupported.additional.text", error.earliestSupportedVersion)

    val linkBuilder = HtmlBuilder()
      .append(linkAction).nbsp()
      .append(linkAdditionalText)

    return builder
      .append(text)
      .append(HtmlChunk.p().attr("align", "left").child(linkBuilder.toFragment()))
  }
}