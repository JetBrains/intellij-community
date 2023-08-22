// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.action.ExternalSystemAction
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class ImportProjectFromScriptAction: ExternalSystemAction() {
  override fun isEnabled(e: AnActionEvent): Boolean = true

  override fun isVisible(e: AnActionEvent): Boolean {
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    val project = e.getData(CommonDataKeys.PROJECT) ?: return false

    return GradleConstants.KNOWN_GRADLE_FILES.contains(virtualFile.name)
           && GradleSettings.getInstance(project).getLinkedProjectSettings(virtualFile.parent.path) == null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val externalProjectPath = getDefaultPath(virtualFile)
    linkAndRefreshGradleProject(externalProjectPath, project)
  }

  private fun getDefaultPath(file: VirtualFile): String {
    return if (file.isDirectory) file.path else file.parent.path
  }
}