// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.commit

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class CommitToolWindowViewModel(
  private val project: Project,
  cs: CoroutineScope,
) {
  val commitTwEnabled: StateFlow<Boolean> = flow {
    durable {
      emitAll(ChangesViewApi.getInstance().isCommitToolWindowEnabled(project.projectId()))
    }
  }.stateIn(cs, SharingStarted.Eagerly, true)

  val canExcludeFromCommit: StateFlow<Boolean> = flow {
    durable {
      emitAll(ChangesViewApi.getInstance().canExcludeFromCommit(project.projectId()))
    }
  }.stateIn(cs, SharingStarted.Eagerly, false)

  val editedCommit: StateFlow<EditedCommitPresentation?> = flow {
    durable {
      emitAll(ChangesViewApi.getInstance().getEditedCommit(project.projectId()))
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  val diffPreviewOnDoubleClickOrEnter: Boolean
    get() =
      if (commitTwEnabled.value) VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
      else VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK
}