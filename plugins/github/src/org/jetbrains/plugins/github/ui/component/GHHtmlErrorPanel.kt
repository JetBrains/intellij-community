// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.net.UnknownHostException

object GHHtmlErrorPanel {
  @Nls
  fun getLoadingErrorText(error: Throwable): String {
    if (error is GithubStatusCodeException && error.error != null && error.error!!.message != null) {
      val githubError = error.error!!
      val message = githubError.message!!.removePrefix("[").removeSuffix("]")
      val builder = HtmlBuilder().append(message)
      if (message.startsWith("Could not resolve to a Repository", true)) {
        @NlsSafe
        val explanation = " Either repository doesn't exist or you don't have access. The most probable cause is that OAuth App access restrictions are enabled in organization."
        builder.append(explanation)
      }

      val errors = githubError.errors?.map { e ->
        HtmlChunk.text(e.message ?: GithubBundle.message("gql.error.in.field", e.code, e.resource, e.field.orEmpty()))
      }
      if (!errors.isNullOrEmpty()) builder.append(": ").append(HtmlChunk.br()).appendWithSeparators(HtmlChunk.br(), errors)
      return builder.toString()
    }

    // Is encountered sometimes after regaining connection,
    // but without this case only 'api.github.com' is shown as a description.
    if (error is UnknownHostException) {
      return GithubBundle.message("unknown.host.error", error.message)
    }

    return error.message ?: GithubBundle.message("unknown.loading.error")
  }
}
