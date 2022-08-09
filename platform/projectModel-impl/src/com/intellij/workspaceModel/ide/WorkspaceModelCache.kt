// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.annotations.ApiStatus

/**
 * Plugins aren't supposed to use this interface directly, the cache is loaded and saved automatically by [WorkspaceModel].
 */
@ApiStatus.Internal
interface WorkspaceModelCache {
  val enabled: Boolean

  fun loadCache(): EntityStorage?

  /**
   * Save workspace model caches
   */
  fun saveCacheNow()

  companion object {
    fun getInstance(project: Project): WorkspaceModelCache? = project.getService(WorkspaceModelCache::class.java)?.takeIf { it.enabled }
  }
}
