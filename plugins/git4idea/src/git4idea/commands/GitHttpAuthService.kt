/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands

import com.intellij.externalProcessAuthHelper.AuthenticationGate
import com.intellij.externalProcessAuthHelper.AuthenticationMode
import com.intellij.externalProcessAuthHelper.ExternalProcessHandlerService
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import git4idea.http.GitAskPassApp
import git4idea.http.GitAskPassAppHandler
import java.io.File
import java.util.*

/**
 * Provides the authentication mechanism for Git HTTP connections.
 */
abstract class GitHttpAuthService : ExternalProcessHandlerService<GitHttpAuthenticator>("intellij-git-askpass",
                                                                                        GitAskPassAppHandler.HANDLER_NAME,
                                                                                        GitAskPassApp::class.java) {
  override fun createRpcRequestHandlerDelegate(): Any {
    return InternalRequestHandlerDelegate()
  }

  /**
   * Creates new [GitHttpAuthenticator] that will be requested to handle username and password requests from Git.
   */
  abstract fun createAuthenticator(project: Project,
                                   urls: Collection<String>,
                                   workingDirectory: File,
                                   authenticationGate: AuthenticationGate,
                                   authenticationMode: AuthenticationMode): GitHttpAuthenticator

  /**
   * Internal handler implementation class, it is made public to be accessible via XML RPC.
   */
  inner class InternalRequestHandlerDelegate : GitAskPassAppHandler {
    override fun handleInput(handlerNo: String, arg: String): String {
      val handler = getHandler(UUID.fromString(handlerNo))
      val usernameNeeded = StringUtilRt.startsWithIgnoreCase(arg, "username") //NON-NLS
      val split = arg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val url = if (split.size > 2) parseUrl(split[2]) else ""
      return getDefaultValueIfCancelled(
        { if (usernameNeeded) handler.askUsername(url) else handler.askPassword(url) }, "")
    }
  }

  companion object {
    private fun parseUrl(url: String): String {
      // un-quote and remove the trailing colon
      var url = url
      url = StringUtil.trimStart(url, "'")
      url = StringUtil.trimEnd(url, ":")
      url = StringUtil.trimEnd(url, "'")
      return url
    }

    fun <T> getDefaultValueIfCancelled(operation: Computable<T>, defaultValue: T): T {
      return try {
        operation.compute()
      }
      catch (pce: ProcessCanceledException) {
        defaultValue
      }
    }
  }
}