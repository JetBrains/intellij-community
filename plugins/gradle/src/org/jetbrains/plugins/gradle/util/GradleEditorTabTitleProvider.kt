// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Vladislav.Soroka
 */
class GradleEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (!GradleConstants.KNOWN_GRADLE_FILES.contains(file.name)) return null

    val fileParent = file.parent ?: return null

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: return null
    val manager = ExternalSystemModulePropertyManager.getInstance(module)

    if (manager.getExternalSystemId() != GradleConstants.SYSTEM_ID.id) return null

    val projectPath = manager.getLinkedProjectPath() ?: return null

    if (FileUtil.pathsEqual(projectPath, fileParent.path)) {
      return "${file.name} (${manager.getLinkedProjectId()})"
    }

    return null
  }
}