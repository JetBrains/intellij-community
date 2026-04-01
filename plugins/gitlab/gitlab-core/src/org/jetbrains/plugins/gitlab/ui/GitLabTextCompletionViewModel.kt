// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.collectIncrementallyTo
import com.intellij.collaboration.util.computeEmitting
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.plugins.gitlab.api.dto.GitLabGroupRestDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject

/**
 * Short-lived view model for mentions variants completion.
 */
interface GitLabTextCompletionViewModel {
  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val author: GitLabUserDTO
  val participants: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>>
  val foundProjectUsers: StateFlow<ComputedResult<List<GitLabUserDTO>>?>
  val foundGroups: StateFlow<ComputedResult<List<GitLabGroupRestDTO>>?>
  fun setSearchPrefix(prefix: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GitLabTextCompletionViewModelImpl(
  parentCs: CoroutineScope,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
) : GitLabTextCompletionViewModel {

  private val cs = parentCs.childScope(javaClass.name)

  override val author: GitLabUserDTO = mergeRequest.author

  private val prefixSearchChangedSignal = MutableStateFlow<String?>(null)

  override fun setSearchPrefix(prefix: String) {
    prefixSearchChangedSignal.tryEmit(prefix)
  }

  override val foundGroups: StateFlow<ComputedResult<List<GitLabGroupRestDTO>>?> =
    prefixSearchChangedSignal.transformLatest { searchString ->
      if (searchString == null) return@transformLatest
      computeEmitting {
        projectData.searchGroups(searchString)
      }
    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val foundProjectUsers: StateFlow<ComputedResult<List<GitLabUserDTO>>?> =
    prefixSearchChangedSignal.transformLatest { searchString ->
      if (searchString == null) return@transformLatest
      computeEmitting {
        projectData.searchProjectUsers(searchString)
      }
    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val participants: StateFlow<IncrementallyComputedValue<List<GitLabUserDTO>>> =
    mergeRequest.mergeRequestReloadSignal.withInitial(Unit).transformLatest {
      mergeRequest.getParticipantsBatches().collectIncrementallyTo(this)
    }.stateIn(cs, SharingStarted.Lazily, IncrementallyComputedValue.loading())
}


