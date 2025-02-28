// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

private class GradleEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (!GradleConstants.KNOWN_GRADLE_FILES.contains(file.name)) {
      return null
    }

    val fileParent = file.parent ?: return null

    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val module = ApplicationManager.getApplication().runReadAction(Computable { projectFileIndex.getModuleForFile(file) })
                 ?: return null

    return getCustomTitle(module, file, fileParent)
  }

  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    if (!GradleConstants.KNOWN_GRADLE_FILES.contains(file.name)) {
      return null
    }

    val fileParent = file.parent ?: return null

    val projectFileIndex = project.serviceAsync<ProjectFileIndex>()
    val module = readAction { projectFileIndex.getModuleForFile(file) }
                 ?: return null

    return getCustomTitle(module, file, fileParent)
  }

  private fun getCustomTitle(module: Module, file: VirtualFile, fileParent: VirtualFile): String? {
    val manager = ExternalSystemModulePropertyManager.getInstance(module)

    if (manager.getExternalSystemId() != GradleConstants.SYSTEM_ID.id) {
      return null
    }

    val projectPath = manager.getLinkedProjectPath() ?: return null

    if (FileUtil.pathsEqual(projectPath, fileParent.path)) {
      return "${file.name} (${manager.getLinkedProjectId()})"
    }

    return null
  }
}