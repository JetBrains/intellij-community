// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDownloadSourcesAction : ExternalSystemAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      event.presentation.isEnabled = false
      return
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    project.gradleCoroutineScope.launch {
      if (ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProjectAsync(project, GradleConstants.SYSTEM_ID)) {
        readAction {
          ExternalSystemActionsCollector.trigger(project, GradleConstants.SYSTEM_ID, this@GradleDownloadSourcesAction, event)
        }
        val spec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).withVmOptions("-Didea.gradle.download.sources.force=true")
        ExternalSystemUtil.refreshProjects(spec)
      }
    }
  }
}