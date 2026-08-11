// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.exceptions

import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.net.UnknownHostException

object GHAPIExceptionUtil {
  internal const val OAUTH_ERROR_SUPPORT_LINK = "https://youtrack.jetbrains.com/articles/SUPPORT-A-2404"
  private const val OAUTH_ERROR_MESSAGE_PREFIX = "Could not resolve to a Repository"

  /**
   * Returns a user-presentable error message.
   *
   * @return An error description that may contain HTML for rich formatting
   *         or plain text depending on the exception type. Callers should handle both cases.
   */
  @Nls
  fun getPresentableMessage(e: Throwable): @Nls String =
    when (e) {
      is GithubStatusCodeException -> getStatusCodeErrorMessage(e)
      is GithubConfusingException -> getConfusingErrorMessage(e)
      is GithubAuthenticationException -> GithubBundle.message("authorization.failed")
      // Is encountered sometimes after regaining connection, but otherwise only 'api.github.com' is shown as a description.
      is UnknownHostException -> GithubBundle.message("unknown.host.error", e.message)
      else -> ExceptionUtil.getPresentableMessage(e)
    }

  @NlsSafe
  private fun getStatusCodeErrorMessage(error: GithubStatusCodeException): String {
    if (error.error != null && error.error!!.message != null) {
      val githubError = error.error!!
      val message = githubError.message!!.removePrefix("[").removeSuffix("]")
      val builder = HtmlBuilder().append(message)
      if (message.startsWith(OAUTH_ERROR_MESSAGE_PREFIX, ignoreCase = true)) {
        builder.appendOAuthErrorMessage()
      }
      val errors = githubError.errors?.map { e ->
        HtmlChunk.text(e.message ?: GithubBundle.message("gql.error.in.field", e.code, e.resource, e.field.orEmpty()))
      }
      if (!errors.isNullOrEmpty()) builder.append(": ").append(HtmlChunk.br()).appendWithSeparators(HtmlChunk.br(), errors)
      return builder.toString()
    }

    return error.message ?: GithubBundle.message("unknown.loading.error")
  }

  @NlsSafe
  private fun getConfusingErrorMessage(error: GithubConfusingException): String {
    val message = error.message
    if (message != null) {
      val messageParts = message.split("\n\n")
      if (messageParts.size >= 2) {
        val (title, description) = messageParts
        if (description.startsWith(OAUTH_ERROR_MESSAGE_PREFIX, ignoreCase = true)) {
          return HtmlBuilder()
            .appendOAuthErrorMessage(title)
            .toString()
        }
      }
    }
    return message ?: GithubBundle.message("unknown.loading.error")
  }

  private fun HtmlBuilder.appendOAuthErrorMessage(title: String? = null): HtmlBuilder = apply {
    title?.let { append(it) }
    append(HtmlChunk.br())
    append(GithubBundle.message("oauth.restrictions.error") + " ")
    appendLink(OAUTH_ERROR_SUPPORT_LINK, GithubBundle.message("error.support.link.label"))
  }
}
