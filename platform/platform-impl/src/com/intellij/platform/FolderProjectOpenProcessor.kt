// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import org.jetbrains.annotations.Nls

internal class FolderProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: @Nls String = IdeBundle.message("folder.open.processor.name")

  override fun canOpenProject(file: VirtualFile): Boolean {
    return Registry.`is`("ide.allow.folder.as.project", false) && file.isDirectory
  }

  override fun isProjectFile(file: VirtualFile): Boolean {
    return false
  }

  override fun lookForProjectsInDirectory(): Boolean {
    return false
  }

  override suspend fun openProjectAsync(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    val projectPath = virtualFile.toNioPath()
    return ProjectManagerEx.getInstanceEx().openProjectAsync(
      projectIdentityFile = projectPath,
      options = OpenProjectTask {
        projectRootDir = projectPath
        projectName = virtualFile.name
        createModule = false
      })
  }
}