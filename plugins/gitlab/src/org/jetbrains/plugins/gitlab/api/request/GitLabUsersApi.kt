// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.request

import com.intellij.collaboration.api.httpclient.HttpClientUtil.checkResponse
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflatedInputStreamBodyHandler
import com.intellij.collaboration.api.httpclient.sendAndAwaitCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabGQLQueries
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDto
import java.awt.Image
import javax.imageio.ImageIO

object GitLabUsersApi : GitLabApiRequestsBase() {

  suspend fun GitLabApi.getCurrentUser(server: GitLabServerPath): GitLabUserDto? {
    val request = gqlQuery(server, GitLabGQLQueries.getCurrentUser)
    return loadGQLResponse(request, "currentUser")
  }

  suspend fun GitLabApi.loadImage(uri: String): Image? {
    val response = client.sendAndAwaitCancellable(request(uri).GET().build(), inflatedInputStreamBodyHandler())
    return withContext(Dispatchers.IO) {
      checkResponse(response)
      response.body().use(ImageIO::read)
    }
  }
}