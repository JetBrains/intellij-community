// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import java.util.concurrent.ConcurrentHashMap

@Service
class GitLabMergeRequestDiffModelRepository(
  private val project: Project,
  cs: CoroutineScope
) {

  private val cs = cs.childScope(Dispatchers.Default)

  private val models = ConcurrentHashMap<ModelId, Flow<GitLabDiffRequestChainViewModel>>()

  fun getShared(connection: GitLabProjectConnection, mr: GitLabMergeRequestId): Flow<GitLabDiffRequestChainViewModel> {
    val id = ModelId(connection.id, connection.repo.repository, GitLabMergeRequestId.Simple(mr))
    return models.getOrPut(id) {
      channelFlow {
        val model = MutableGitLabDiffRequestChainViewModel(project, this, connection.projectData, mr)
        send(model)

        launch {
          connection.awaitClose()
        }
        awaitClose()
      }.onCompletion { models.remove(id) }
        .shareIn(cs, SharingStarted.WhileSubscribed(0, 0), 1)
    }
  }

  private data class ModelId(val connectionId: String, val glProject: GitLabProjectCoordinates, val mr: GitLabMergeRequestId)
}