// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface JpsGlobalModelSynchronizer {
  fun loadInitialState(
    environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName,
    mutableStorage: MutableEntityStorage,
    initialEntityStorage: VersionedEntityStorage,
    loadedFromCache: Boolean,
  ): () -> Unit

  /**
   * Adds a job that must complete before the delayed global workspace model loading can proceed.
   * This prevents race conditions between project synchronization and global synchronization.
   * 
   * This method is thread-safe and supports multiple concurrent project synchronization jobs,
   * which is important since multiple projects can be opened in parallel.
   * 
   * The issue: When loading a project from cache, both global sync and project sync use background
   * write actions. If global sync executes first, it can delay the project sync which is part of
   * the InitProjectActivity lifecycle and must not be delayed.
   * 
   * @param job The project synchronization job that should complete before delayed global loading.
   *            The job will be automatically removed from tracking when it completes.
   */
  fun setProjectSynchronizationJob(job: Job)

  fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)

  companion object {
    fun getInstance(): JpsGlobalModelSynchronizer = service()
  }
}