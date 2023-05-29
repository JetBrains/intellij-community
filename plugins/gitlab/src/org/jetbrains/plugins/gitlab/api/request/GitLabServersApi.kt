// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import java.net.http.HttpResponse

suspend fun GitLabApi.checkIsGitLabServer(server: GitLabServerPath): Boolean {
  val uri = server.restApiUri.resolveRelative("metadata")
  val request = request(uri).GET().build()
  val response = sendAndAwaitCancellable(request, HttpResponse.BodyHandlers.discarding())
  return response.headers().map().keys.any {
    it.contains("gitlab", true)
  }
}