// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.vcs.impl.shared.rpc.UpdateStatus
import fleet.kernel.ref
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShelfTreeGroupingUpdateScheduler {

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  internal suspend fun requestUpdateGrouping(groupingKeys: Set<String>, project: Project): UpdateStatus {
    return try {
      updateSemaphore.withPermit {
        val projectRef = withKernel {
          project.asEntity().ref()
        }
        return@withPermit RemoteShelfApi.getInstance().applyTreeGrouping(projectRef, groupingKeys).await()
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
