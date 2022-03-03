// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.PlatformProjectOpenProcessor
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class WorkspaceModelRecoveryAction : RecoveryAction {
  override val performanceRate: Int
    get() = 4000
  override val presentableName: String
    get() = IdeBundle.message("invalidate.workspace.model.recovery.action.presentable.name")
  override val actionKey: String
    get() = "reload-workspace-model"

  override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    val project = recoveryScope.project
    val file = Paths.get(project.basePath!!)
    WorkspaceModelCacheImpl.invalidateCaches()
    ApplicationManager.getApplication().invokeAndWait {
      ProjectManagerEx.getInstanceEx().closeAndDispose(project)
    }
    val result = CompletableFuture<AsyncRecoveryResult>()
    ApplicationManager.getApplication().invokeLater({
      val projectFuture = ProjectUtil.openOrImportAsync(file, PlatformProjectOpenProcessor.createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, null))
      projectFuture.handle { r, th -> if (th == null) result.complete(AsyncRecoveryResult(ProjectRecoveryScope(r!!), emptyList())) else result.completeExceptionally(th)};
    }, ModalityState.NON_MODAL)
    return result
  }

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = recoveryScope is ProjectRecoveryScope && recoveryScope.project.basePath?.let { Files.isDirectory(Paths.get(it)) } == true
}