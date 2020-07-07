// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
class GHEnterpriseServerMetadataLoader : Disposable {

  private val apiRequestExecutor = GithubApiRequestExecutor.Factory.getInstance().create()
  private val serverMetadataRequests = ConcurrentHashMap<GithubServerPath, CompletableFuture<GHEnterpriseServerMeta>>()
  private val indicators = ConcurrentHashMap.newKeySet<ProgressIndicator>()

  @CalledInAny
  fun loadMetadata(server: GithubServerPath): CompletableFuture<GHEnterpriseServerMeta> {
    require(!server.isGithubDotCom) { "Cannot retrieve server metadata from github.com" }
    return serverMetadataRequests.getOrPut(server) {
      val indicator = EmptyProgressIndicator(ModalityState.any())
      indicators.add(indicator)
      ProgressManager.getInstance().submitIOTask(indicator) {
        val metaUrl = server.toApiUrl() + "/meta"
        apiRequestExecutor.execute(GithubApiRequest.Get.json<GHEnterpriseServerMeta>(metaUrl))
      }.completionOnEdt {
        indicators.remove(indicator)
      }
    }
  }

  override fun dispose() {
    indicators.forEach { it.cancel() }
  }
}