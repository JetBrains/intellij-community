// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.suppression

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleSuppressHighlightingListener : ExternalSystemTaskNotificationListener {

  override fun onSuccess(proojecPath: String, id: ExternalSystemTaskId) =
    processAllProjects(id, GradleSuspendTypecheckingService::resumeHighlighting)

  override fun onFailure(proojecPath: String, id: ExternalSystemTaskId, exception: Exception) =
    processAllProjects(id, GradleSuspendTypecheckingService::suspendHighlighting)

  private fun processAllProjects(id: ExternalSystemTaskId, action: GradleSuspendTypecheckingService.(String) -> Unit) {
    val project = id.findProject() ?: return
    val suspender = project.service<GradleSuspendTypecheckingService>()
    val allSettings = GradleSettings.getInstance(project).linkedProjectsSettings
    for (settings in allSettings) {
      suspender.action(settings.externalProjectPath)
    }
  }

}