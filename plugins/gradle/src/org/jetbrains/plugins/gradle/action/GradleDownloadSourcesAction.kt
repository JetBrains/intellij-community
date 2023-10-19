// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider
import java.util.concurrent.CountDownLatch

class GradleDownloadSourcesAction : ExternalSystemAction() {

  private companion object {
    private const val REGISTRY_KEY = "gradle.download.sources"
  }

  private class RefreshHandler(private val latch: CountDownLatch) : ExternalProjectRefreshCallback {
    override fun onSuccess(externalTaskId: ExternalSystemTaskId, externalProject: DataNode<ProjectData?>?) = latch.countDown()
    override fun onFailure(errorMessage: String, errorDetails: String?) = latch.countDown()
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.getProject()
    if (project == null) {
      event.presentation.isEnabled = false
      return
    }
    val systemIds = getSystemIds(event)
    if (systemIds.isEmpty()) {
      event.presentation.isEnabled = false
      return
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    GradleCoroutineScopeProvider.getInstance(project).cs
      .launch {
        if (ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProjectAsync(project, systemIds)) {
          toggleRegistry(true)
          val latch = CountDownLatch(systemIds.size)
          systemIds.forEach { id ->
            ExternalSystemActionsCollector.trigger(project, id, this@GradleDownloadSourcesAction, event)
            val spec = ImportSpecBuilder(project, id).callback(RefreshHandler(latch))
            ExternalSystemUtil.refreshProjects(spec)
          }
          launch {
            latch.await()
            toggleRegistry(false)
          }
        }
    }
  }

  private fun toggleRegistry(downloadSources: Boolean) {
    Registry.get(REGISTRY_KEY).setValue(downloadSources)
  }

  private fun getSystemIds(event: AnActionEvent): List<ProjectSystemId> = event.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID)
    .let {
      if (it == null) {
        val systemIds = mutableListOf<ProjectSystemId>()
        ExternalSystemManager.EP_NAME.forEachExtensionSafe { systemIds.add(it.systemId) }
        systemIds
      }
      else {
        listOf(it)
      }
    }
}