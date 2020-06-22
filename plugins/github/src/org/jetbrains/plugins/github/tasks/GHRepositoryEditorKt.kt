// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.tasks

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.exceptions.GithubParseException

private object GHRepositoryEditorKt {
  fun askToken(project: Project, host: String): String? {
    val server = tryParse(host) ?: return null

    return GithubAuthenticationManager.getInstance().login(
      project, null,
      GHLoginRequest(server = server)
    )?.token
  }

  private fun tryParse(host: String): GithubServerPath? =
    try {
      GithubServerPath.from(host)
    }
    catch (ignored: GithubParseException) {
      null
    }
}