// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.exceptions.GHAPIExceptionUtil
import org.jetbrains.plugins.github.i18n.GithubBundle

internal object GHGitErrorMessagesUtils {
  @Nls
  internal fun oauthRestrictionMessage(errorOutput: List<String>): String? {
    return if (errorOutput.any { errorOutputLine ->
        errorOutputLine.lowercase().let {
          it.contains("fatal: repository") && it.contains("not found") ||
          it.contains("fatal: unable to access") && it.contains("the requested url returned error: 403")
        }
      }) {
      GithubBundle.message("git.operation.oauth.restrictions.error", GHAPIExceptionUtil.OAUTH_ERROR_SUPPORT_LINK)
    }
    else null
  }
}