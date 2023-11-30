// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * There are hundreds of usages where ProjectRootManager is used as dependency for cached value, however
 * real intention is to invalidate caches when indexes are changed, not the project (because cached value depends on indexes contents, not
 * on project model). Subscription to ProjectRootManager worked because changes in project model causes changes in indexes, and these
 * two changes were processed in one single dumb mode. Now that single dumb mode is not the case anymore, and code that had that assumption
 * about dumb mode does not work properly now.
 * <p>
 * All these usages should be migrated to new API to subscribe to indexes directly (IDEA-314451).
 * Until then, we increment ProjectRootManager on dumb mode exit to invalidate caches timely and provide compatibility with existing code.
 */
@Obsolete
class ProjectRootManagerOnEndOfDumbModeIncrementer(private val project: Project) : DumbService.DumbModeListener {
  override fun exitDumbMode() {
    ProjectRootManager.getInstance(project).incModificationCount()
  }
}