// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.repo.GitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
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

  /**
   * @param viewedFiles The key is the path of a file, the value is the SHA for which last file change was seen.
   *                    The SHA should be a commit hash in which the corresponding file was changed.
   */
  @Serializable
  data class MRViewedState(
    val id: MRId,
    val lastUpdated: Long = System.currentTimeMillis(),
    val viewedFiles: Map<String, String> = mapOf()
  )

  fun isViewed(glProject: GitLabProjectCoordinates, iid: String,
               gitRepository: GitRepository,
               filePath: FilePath, sha: String): Boolean {
    val relPath = VcsFileUtil.relativePath(gitRepository.root, filePath)

    return state.statesMap[MRId(glProject, iid)]?.let {
      it.viewedFiles[relPath] == sha
    } ?: false
  }

  fun markViewed(glProject: GitLabProjectCoordinates, iid: String,
                 gitRepository: GitRepository,
                 filePathsAndShas: Iterable<Pair<FilePath, String>>,
                 viewed: Boolean) {
    val relPathsAndShas = filePathsAndShas.map { VcsFileUtil.relativePath(gitRepository.root, it.first) to it.second }
    updateStateOrCreateAndCleanup(MRId(glProject, iid)) { st ->
      if (viewed) {
        MRViewedState(id = st.id, viewedFiles = st.viewedFiles + relPathsAndShas)
      }
      else {
        MRViewedState(id = st.id, viewedFiles = st.viewedFiles - relPathsAndShas.map { it.first }.toSet())
      }
    }
  }

  override fun loadState(state: State) {
    super.loadState(state)
    stateOfState.value = state
  }

  private inline fun updateStateOrCreateAndCleanup(id: MRId, stateUpdater: (MRViewedState) -> MRViewedState) {
    stateOfState.value = updateState { st ->
      val statesWithoutCurrent = st.states.filterNot { it.id == id }
      val newState = stateUpdater(st.statesMap[id] ?: MRViewedState(id))
      val newStates = statesWithoutCurrent + listOf(newState)

      val staleBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(STALE_TIMEOUT_KEY).toLong()).toMillis()
      st.copy(states = newStates.filterNot { it.lastUpdated < staleBeforeTime || it.viewedFiles.isEmpty() })
    }
  }
}