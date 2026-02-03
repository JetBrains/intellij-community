// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny

@Service(Service.Level.PROJECT)
internal class ConflictsResolutionService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  @CalledInAny
  fun showConflictResolutionDialog(changes: List<Change>) {
    cs.launch {
      ChangesViewApi.getInstance().showResolveConflictsDialog(project.projectId(), changes.map {
        ChangeId.getId(it)
      })
    }
  }
}