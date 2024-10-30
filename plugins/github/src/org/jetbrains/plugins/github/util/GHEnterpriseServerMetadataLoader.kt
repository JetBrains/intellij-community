// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestOperation
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.executeSuspend

@Service
internal class GHEnterpriseServerMetadataLoader(serviceCs: CoroutineScope) {
  private val cs = serviceCs.childScope(javaClass.name)

  private val apiRequestExecutor = GithubApiRequestExecutor.Factory.getInstance().create()
  private val cache = Caffeine.newBuilder()
    .build<GithubServerPath, Deferred<GHEnterpriseServerMeta>>()

  suspend fun loadMetadata(server: GithubServerPath): GHEnterpriseServerMeta {
    require(!server.isGithubDotCom) { "Cannot retrieve server metadata from github.com" }
    return cache.get(server) {
      val metaUrl = server.toApiUrl() + "/meta"
      cs.async {
        apiRequestExecutor.executeSuspend(
          GithubApiRequest.Get.json<GHEnterpriseServerMeta>(metaUrl)
            .withOperation(GithubApiRequestOperation.RestGetServerMetadata)
            .withOperationName("get server metadata")
        )
      }
    }.await()
  }
}