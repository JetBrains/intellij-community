// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.openapi.components.Service
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class GitLabMergeRequestDiffBridgeRepository(cs: CoroutineScope) {

  private val cs = cs.childScope(Dispatchers.Default)

  private val models = ConcurrentHashMap<ModelId, GitLabMergeRequestDiffBridge>()

  fun get(connection: GitLabProjectConnection, mr: GitLabMergeRequestId): GitLabMergeRequestDiffBridge {
    connection.checkIsOpen()
    val id = ModelId(connection.id, connection.repo.repository, GitLabMergeRequestId.Simple(mr))
    return models.getOrPut(id) {
      GitLabMergeRequestDiffBridge().also {
        cs.launch {
          connection.awaitClose()
          models.remove(id)
        }
      }
    }
  }

  private data class ModelId(val connectionId: String, val glProject: GitLabProjectCoordinates, val mr: GitLabMergeRequestId)
}