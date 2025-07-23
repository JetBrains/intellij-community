// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.project.projectIdOrNull
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ChangeListsViewModel(
  private val project: Project,
  cs: CoroutineScope,
) {
  val areChangeListsEnabled: StateFlow<Boolean> = flow {
    val projectId = project.projectIdOrNull() ?: return@flow
    emitAll(ChangeListsApi.getInstance().areChangeListsEnabled(projectId))
  }.stateIn(cs, SharingStarted.Companion.Eagerly, false)

  val changes: StateFlow<List<Change>> = flow {
    val projectId = project.projectIdOrNull() ?: return@flow
    emitAll(ChangeListsApi.getInstance().getChangeLists(projectId).map { changeLists ->
      changeLists.flatMap { it.getChangeList(project).changes }
    })
  }.stateIn(cs, SharingStarted.Companion.Eagerly, emptyList())

  companion object {
    fun getInstance(project: Project): ChangeListsViewModel = project.service()
  }
}