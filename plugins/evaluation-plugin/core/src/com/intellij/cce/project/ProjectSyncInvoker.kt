package com.intellij.cce.project

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

class ProjectSyncInvokerParams(val language: Language)

interface ProjectSyncInvoker {
  companion object {
    private val EP_NAME: ExtensionPointName<ProjectSyncInvoker> =
      ExtensionPointName.create("com.intellij.cce.projectSyncInvoker")

    fun getProjectSyncInvoker(params: ProjectSyncInvokerParams): ProjectSyncInvoker {
      return EP_NAME.findFirstSafe { it.isApplicable(params) } ?: throw IllegalStateException("No test runner for $params")
    }
  }

  fun isApplicable(params: ProjectSyncInvokerParams): Boolean

  suspend fun syncProject(project: Project)
}
