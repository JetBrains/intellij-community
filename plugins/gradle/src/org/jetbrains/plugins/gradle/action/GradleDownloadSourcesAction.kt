// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider

class GradleDownloadSourcesAction : ExternalSystemAction() {

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
          systemIds.forEach { id ->
            ExternalSystemActionsCollector.trigger(project, id, this@GradleDownloadSourcesAction, event)
            val spec = ImportSpecBuilder(project, id).withVmOptions("-Didea.gradle.download.sources.force=true")
            ExternalSystemUtil.refreshProjects(spec)
          }
        }
      }
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