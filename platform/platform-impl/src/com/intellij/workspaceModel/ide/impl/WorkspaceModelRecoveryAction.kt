// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class WorkspaceModelRecoveryAction : RecoveryAction {
  override val performanceRate: Int
    get() = 4000
  override val presentableName: String
    get() = IdeBundle.message("invalidate.workspace.model.recovery.action.presentable.name")
  override val actionKey: String
    get() = "reload-workspace-model"

  override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    val project = recoveryScope.project
    val file = Path.of(project.basePath!!)
    WorkspaceModelCacheImpl.invalidateCaches()
    val app = ApplicationManager.getApplication()
    app.invokeAndWait {
      ProjectManager.getInstance().closeAndDispose(project)
    }
    val result = CompletableFuture<AsyncRecoveryResult>()
    (app as ComponentManagerEx).getCoroutineScope().launch {
      val r = ProjectUtil.openOrImportAsync(
        file = file,
        options = OpenProjectTask {
          runConfigurators = true
          isNewProject = !ProjectUtilCore.isValidProjectPath(file)
          useDefaultProjectAsTemplate = true
        }
      )
      result.complete(AsyncRecoveryResult(ProjectRecoveryScope(r!!), emptyList()))
    }
    return result
  }

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean {
    return recoveryScope is ProjectRecoveryScope && recoveryScope.project.basePath?.let {
      Files.isDirectory(Paths.get(it))
    } == true
  }
}