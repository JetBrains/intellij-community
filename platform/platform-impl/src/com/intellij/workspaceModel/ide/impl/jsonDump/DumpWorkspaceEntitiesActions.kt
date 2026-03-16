// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DumpWorkspaceEntitiesToClipboardAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToClipboardAsJson()
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class DumpWorkspaceEntitiesWsmChangeListener(val project: Project, val scope: CoroutineScope) {
  init {
    project.messageBus.connect(project).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        if (!Registry.`is`("ide.workspace.model.dump.on.every.wsm.update")){
          return
        }

        val version = (project.workspaceModel as WorkspaceModelImpl).entityStorage.version
        val fileName = "${System.currentTimeMillis()}-workspace-model-dump-version-${PathUtil.suggestFileName(project.name)}.${project.locationHash}-$version"
        project.service<WorkspaceModelJsonDumpService>()
          .dumpWorkspaceEntitiesToLogFileAsJson(fileName, event.storageAfter, openFileInEditor = false)
      }
    })
  }
}

@ApiStatus.Internal
class DumpWorkspaceEntitiesToLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToLogAsJson()
  }
}

@ApiStatus.Internal
class DumpWorkspaceEntitiesToLogFileAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<WorkspaceModelJsonDumpService>()?.dumpWorkspaceEntitiesToLogFileAsJson()
  }
}
