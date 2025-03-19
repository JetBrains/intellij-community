// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.externalProcessAuthHelper.AuthenticationGate
import com.intellij.externalProcessAuthHelper.AuthenticationMode
import com.intellij.externalProcessAuthHelper.ExternalProcessHandlerService
import com.intellij.externalProcessAuthHelper.ExternalProcessRest
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import git4idea.http.GitAskPassApp
import git4idea.http.GitAskPassAppHandler
import java.io.File
import java.util.*

/**
 * Provides the authentication mechanism for Git HTTP connections.
 */
abstract class GitHttpAuthService : ExternalProcessHandlerService<GitAskPassAppHandler>(
  "intellij-git-askpass",
  GitAskPassApp::class.java
) {
  /**
   * Creates new [GitHttpAuthenticator] that will be requested to handle username and password requests from Git.
   */
  abstract fun createAuthenticator(project: Project,
                                   urls: Collection<String>,
                                   workingDirectory: File,
                                   authenticationGate: AuthenticationGate,
                                   authenticationMode: AuthenticationMode): GitHttpAuthenticator

  override fun handleRequest(handler: GitAskPassAppHandler, requestBody: String): String? {
    return handler.handleInput(requestBody)
  }

  fun registerHandler(authenticator: GitHttpAuthenticator, disposable: Disposable): UUID {
    return registerHandler(AuthAppHandler(authenticator), disposable)
  }

  private class AuthAppHandler(private val authenticator: GitHttpAuthenticator) : GitAskPassAppHandler {
    override fun handleInput(arg: String): String? {
      val usernameNeeded = StringUtilRt.startsWithIgnoreCase(arg, "username") //NON-NLS
      val split = arg.split(" ")
      val url = if (split.size > 2) parseUrl(split[2]) else ""
      try {
        return if (usernameNeeded) authenticator.askUsername(url) else authenticator.askPassword(url)
      }
      catch (e: ProcessCanceledException) {
        return null
      }
    }

    private fun parseUrl(url: String): String {
      // un-quote and remove the trailing colon
      return url.trimStart { it == '\'' }
        .trimEnd { it == '\'' || it == ':' }
    }
  }
}

private class GitAskPassExternalProcessRest : ExternalProcessRest<GitAskPassAppHandler>(
  GitAskPassAppHandler.ENTRY_POINT_NAME
) {
  override val externalProcessHandler: ExternalProcessHandlerService<GitAskPassAppHandler> get() = service<GitHttpAuthService>()
}
