// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.actions.cache.ReopenProjectRecoveryAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WorkspaceModelRecoveryAction : ReopenProjectRecoveryAction() {

  override val performanceRate: Int
    get() = 4000

  override val presentableName: String
    get() = IdeBundle.message("invalidate.workspace.model.recovery.action.presentable.name")

  override val actionKey: String
    get() = "reload-workspace-model"

  override suspend fun performAsync(recoveryScope: RecoveryScope): AsyncRecoveryResult {
    WorkspaceModelCacheImpl.invalidateCaches()

    val projectPath = closeProject(recoveryScope)

    val newRecoveryScope = openProject(projectPath)

    return AsyncRecoveryResult(newRecoveryScope, emptyList())
  }
}