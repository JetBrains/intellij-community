// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.platform.vcs.impl.shared.rpc.UpdateStatus
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShelfTreeGroupingUpdateScheduler {

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  internal suspend fun requestUpdateGrouping(groupingKeys: Set<String>, project: Project): UpdateStatus {
    return try {
      updateSemaphore.withPermit {
        return@withPermit RemoteShelfApi.getInstance().applyTreeGrouping(project.projectId(), groupingKeys).await()
      }
    }
    catch (_: Exception) {
      UpdateStatus.FAILED
    }
  }

  companion object {
    fun getInstance(project: Project): ShelfTreeGroupingUpdateScheduler = project.service()
  }
}
