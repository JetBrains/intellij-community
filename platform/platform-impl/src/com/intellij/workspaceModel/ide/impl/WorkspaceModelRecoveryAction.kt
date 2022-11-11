// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.PlatformProjectOpenProcessor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

  @OptIn(DelicateCoroutinesApi::class)
  override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    val project = recoveryScope.project
    val file = Paths.get(project.basePath!!)
    WorkspaceModelCacheImpl.invalidateCaches()
    ApplicationManager.getApplication().invokeAndWait {
      ProjectManager.getInstance().closeAndDispose(project)
    }
    val result = CompletableFuture<AsyncRecoveryResult>()
    GlobalScope.launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      val r = ProjectUtil.openOrImportAsync(
        file = file,
        options = PlatformProjectOpenProcessor.createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, null)
      )
      AsyncRecoveryResult(ProjectRecoveryScope(r!!), emptyList())
    }
    return result
  }

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean {
    return recoveryScope is ProjectRecoveryScope && recoveryScope.project.basePath?.let {
      Files.isDirectory(Paths.get(it))
    } == true
  }
}