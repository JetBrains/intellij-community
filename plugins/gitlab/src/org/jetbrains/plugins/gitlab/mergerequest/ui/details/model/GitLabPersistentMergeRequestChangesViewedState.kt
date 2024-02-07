// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import java.time.Duration

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestChangesViewedState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class GitLabPersistentMergeRequestChangesViewedState
  : SerializablePersistentStateComponent<GitLabPersistentMergeRequestChangesViewedState.State>(State()) {
  companion object {
    private const val STALE_TIMEOUT_KEY = "gitlab.viewed.state.stale.timeout"
  }

  private val stateOfState = MutableStateFlow(State())

  val updatesFlow: Flow<Unit> = stateOfState.map { }

  @Serializable
  data class State(
    val states: List<MRViewedState> = listOf()
  ) {
    val statesMap: Map<MRId, MRViewedState> by lazy {
      states.associateBy { it.id }
    }
  }

  @Serializable
  data class MRId(
    val project: GitLabProjectCoordinates,
    val iid: String
  )

  @Serializable
  data class MRViewedState(
    val id: MRId,
    val sha: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val viewedFiles: Set<String> = setOf()
  )

  fun isViewed(mr: GitLabMergeRequest, file: FilePath, sha: String) =
    isViewed(mr.glProject, mr.iid, file, sha)

  fun isViewed(glProject: GitLabProjectCoordinates, iid: String, file: FilePath, sha: String) =
    state.statesMap[MRId(glProject, iid)]?.let {
      it.viewedFiles.contains(file.path) && it.sha == sha
    } ?: false

  fun markViewed(mr: GitLabMergeRequest, files: Iterable<FilePath>, sha: String, viewed: Boolean) =
    markViewed(mr.glProject, mr.iid, files, sha, viewed)

  fun markViewed(glProject: GitLabProjectCoordinates, iid: String, files: Iterable<FilePath>, sha: String, viewed: Boolean) {
    updateStateOrCreateAndCleanup(MRId(glProject, iid)) { st ->
      val alreadyViewedFiles = if (st.sha == sha) st.viewedFiles else setOf()

      if (viewed) {
        MRViewedState(id = st.id, sha = sha, viewedFiles = alreadyViewedFiles + files.map { it.path })
      }
      else {
        MRViewedState(id = st.id, sha = sha, viewedFiles = alreadyViewedFiles - files.map { it.path }.toSet())
      }
    }
  }

  override fun loadState(state: State) {
    super.loadState(state)
    stateOfState.value = state
  }

  private fun updateStateOrCreateAndCleanup(id: MRId, stateUpdater: (MRViewedState) -> MRViewedState) {
    stateOfState.value = updateState { st ->
      val statesWithoutCurrent = st.states.filterNot { it.id == id }
      val newState = stateUpdater(st.statesMap[id] ?: MRViewedState(id, ""))
      val newStates = statesWithoutCurrent + listOf(newState)

      val staleBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(STALE_TIMEOUT_KEY).toLong()).toMillis()
      st.copy(states = newStates.filterNot { it.lastUpdated < staleBeforeTime || it.viewedFiles.isEmpty() })
    }
  }
}