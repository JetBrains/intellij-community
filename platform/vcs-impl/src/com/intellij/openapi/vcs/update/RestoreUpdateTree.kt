// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectReloadState
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import org.jetbrains.annotations.ApiStatus.Internal

@State(name = "RestoreUpdateTree", storages = [Storage(StoragePathMacros.CACHE_FILE)])
@Service(Service.Level.PROJECT)
class RestoreUpdateTree : PersistentStateComponent<UpdateInfoState> {
  private var updateInfo: UpdateInfo? = null

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RestoreUpdateTree = project.service<RestoreUpdateTree>()
  }

  internal class RestoreUpdateTreeStartUpActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val updateTree = project.serviceAsync<RestoreUpdateTree>()
      val updateInfo = updateTree.updateInfo
      if (updateInfo != null && !updateInfo.isEmpty && project.serviceAsync<ProjectReloadState>().isAfterAutomaticReload) {
        val actionInfo = updateInfo.actionInfo
        if (actionInfo != null) {
          val projectLevelVcsManager = project.serviceAsync<ProjectLevelVcsManager>() as ProjectLevelVcsManagerEx
          projectLevelVcsManager.showUpdateProjectInfo(updateInfo.fileInformation,
                                                       VcsBundle.message("action.display.name.update"),
                                                       actionInfo,
                                                       false)
          project.serviceAsync<CommittedChangesCache>().refreshIncomingChangesAsync()
        }
      }
      updateTree.updateInfo = null
    }
  }

  @Internal
  override fun getState(): UpdateInfoState {
    return updateInfo?.writeExternal() ?: UpdateInfoState()
  }

  @Internal
  override fun loadState(state: UpdateInfoState) {
    updateInfo = readUpdateInfoState(state).takeIf { !it.isEmpty }
  }

  fun registerUpdateInformation(updatedFiles: UpdatedFiles?, actionInfo: ActionInfo?) {
    updateInfo = UpdateInfo(updatedFiles, actionInfo)
  }
}
